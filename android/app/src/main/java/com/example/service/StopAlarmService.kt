package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.ArrayDeque
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.StopAlarmNotifier
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Фоновый сервис: следит за геопозицией (даже с выключенным экраном) и
 * срабатывает звуком/вибрацией, когда пользователь подъезжает к выбранной остановке.
 *
 * Запускается из приложения, когда оно открыто (foreground), поэтому НЕ требует
 * разрешения "в фоне всегда" (ACCESS_BACKGROUND_LOCATION) — хватает обычной геолокации.
 */
class StopAlarmService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private var callback: LocationCallback? = null

    private var stopName: String = "Остановка"
    private var targetLat: Double = 0.0
    private var targetLon: Double = 0.0
    private var radius: Double = 350.0
    private var voice: Boolean = false
    private var proximityTracker = StopAlarmProximityTracker()
    private var passageDetector = StopAlarmPassageDetector(0.0, 0.0)
    private var trackingGeneration: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    // Синтезатор речи для голосового оповещения (если включён режим «сигнал + голос»)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingPhrases = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        StopAlarmNotifier.createChannel(this)
        createOngoingChannel()
    }

    private fun ensureTtsInitialized() {
        if (tts != null) return
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = try { tts?.setLanguage(Locale.forLanguageTag("ru-RU")) } catch (_: Exception) { null }
                ttsReady = result != null && result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                if (ttsReady) flushPendingSpeech()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        if (intent == null || !intent.hasExtra(EXTRA_LAT) || !intent.hasExtra(EXTRA_LON)) {
            stopTracking()
            return START_NOT_STICKY
        }
        stopName = intent.getStringExtra(EXTRA_STOP_NAME)?.take(120) ?: "Остановка"
        targetLat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        targetLon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)
        if (!targetLat.isFinite() || !targetLon.isFinite() ||
            targetLat !in -90.0..90.0 || targetLon !in -180.0..180.0
        ) {
            stopTracking()
            return START_NOT_STICKY
        }
        radius = intent.getDoubleExtra(EXTRA_RADIUS, 350.0)
        if (!radius.isFinite() || radius !in 100.0..2_000.0) {
            stopTracking()
            return START_NOT_STICKY
        }
        voice = intent?.getBooleanExtra(EXTRA_VOICE, false) ?: false
        // Re-arming the alarm must fully retire the preceding location callback
        // and any queued speech before a new tracking session becomes current.
        trackingGeneration++
        cancelLocationUpdates()
        pendingPhrases.clear()
        try { tts?.stop() } catch (_: Exception) {}
        proximityTracker = StopAlarmProximityTracker(approachRadiusMeters = radius)
        passageDetector = StopAlarmPassageDetector(targetLat, targetLon)
        // Android gives a newly started foreground service only a few seconds to
        // publish its notification. Do that before binding to a potentially slow
        // TextToSpeech engine.
        try {
            startForegroundCompat()
        } catch (_: SecurityException) {
            stopTracking()
            return START_NOT_STICKY
        }
        activeAlarmStop.value = intent.getStringExtra(EXTRA_STOP_ID)
        if (voice) ensureTtsInitialized()
        scheduleTrackingTimeout(trackingGeneration)
        startLocationUpdates(trackingGeneration)
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notif = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ONGOING_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(ONGOING_ID, notif)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(generation: Long) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (generation != trackingGeneration) return
                val loc = result.lastLocation ?: return
                onLocation(loc)
            }
        }
        callback = cb
        try {
            fused.requestLocationUpdates(request, cb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopTracking()
        }
    }

    private fun onLocation(loc: Location) {
        val fixTimeMs = loc.elapsedRealtimeNanos / 1_000_000L
        val fixAgeMs = SystemClock.elapsedRealtime() - fixTimeMs
        if (fixAgeMs !in 0L..30_000L) return
        val results = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, targetLat, targetLon, results)
        val dist = results[0].toDouble()
        val accuracy = if (loc.hasAccuracy()) loc.accuracy else DEFAULT_UNREPORTED_ACCURACY_METERS

        val passedStop = passageDetector.update(loc.latitude, loc.longitude, accuracy, fixTimeMs)
        proximityTracker.update(dist, accuracy, passedStop).forEach { event ->
            when (event) {
                StopAlarmProximityTracker.Event.APPROACH -> {
                    StopAlarmNotifier.notifyApproaching(applicationContext, stopName, dist.toInt())
                    if (voice) speak("До остановки $stopName около трёхсот пятидесяти метров. Готовьтесь к выходу.")
                }
                StopAlarmProximityTracker.Event.ARRIVAL -> {
                    StopAlarmNotifier.notifyArrived(applicationContext, stopName)
                    callback?.let { fused.removeLocationUpdates(it) }
                    callback = null
                    if (voice) {
                        speak("Остановка $stopName. Пора выходить.")
                        // Оставляем процесс живым, пока TTS заканчивает обе фразы.
                        val generation = trackingGeneration
                        mainHandler.postDelayed(
                            { if (generation == trackingGeneration) stopTracking() },
                            12_000L
                        )
                    } else {
                        stopTracking()
                    }
                }
                StopAlarmProximityTracker.Event.TIMEOUT -> stopTracking()
            }
        }
    }

    private fun scheduleTrackingTimeout(generation: Long) {
        cancelTrackingTimeout()
        val startedAt = SystemClock.elapsedRealtime()
        val runnable = Runnable {
            if (generation != trackingGeneration) return@Runnable
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (proximityTracker.onElapsed(elapsed).contains(StopAlarmProximityTracker.Event.TIMEOUT)) {
                stopTracking()
            }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, MAX_TRACKING_DURATION_MS)
    }

    private fun cancelTrackingTimeout() {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
    }

    private fun speak(phrase: String) {
        if (!ttsReady) {
            pendingPhrases.addLast(phrase)
            return
        }
        try {
            tts?.speak(phrase, TextToSpeech.QUEUE_ADD, null, "stop_alarm_${System.nanoTime()}")
        } catch (_: Exception) {}
    }

    private fun flushPendingSpeech() {
        while (pendingPhrases.isNotEmpty()) speak(pendingPhrases.removeFirst())
    }

    private fun stopTracking() {
        activeAlarmStop.value = null
        trackingGeneration++
        proximityTracker.cancel()
        cancelLocationUpdates()
        cancelTrackingTimeout()
        pendingPhrases.clear()
        try { tts?.stop() } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun cancelLocationUpdates() {
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
    }

    private fun createOngoingChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ONGOING_CHANNEL,
                "Отслеживание остановки",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Постоянное уведомление, пока приложение следит за остановкой"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPi = launchIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val stopIntent = Intent(this, StopAlarmService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stop_marker)
            .setContentTitle("Слежу за остановкой")
            .setContentText("«$stopName» — предупрежу за 350 м и у остановки")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Остановить", stopPi)

        if (contentPi != null) builder.setContentIntent(contentPi)
        return builder.build()
    }

    override fun onDestroy() {
        activeAlarmStop.value = null
        trackingGeneration++
        proximityTracker.cancel()
        cancelLocationUpdates()
        cancelTrackingTimeout()
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        pendingPhrases.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val activeAlarmStop = MutableStateFlow<String?>(null)
        val activeAlarmStopId = activeAlarmStop.asStateFlow()
        const val ACTION_STOP = "com.example.action.STOP_ALARM_SERVICE_STOP"
        private const val EXTRA_STOP_ID = "extra_stop_id"
        private const val EXTRA_STOP_NAME = "extra_stop_name"
        private const val EXTRA_LAT = "extra_lat"
        private const val EXTRA_LON = "extra_lon"
        private const val EXTRA_RADIUS = "extra_radius"
        private const val EXTRA_VOICE = "extra_voice"
        private const val ONGOING_ID = 4202
        private const val ONGOING_CHANNEL = "stop_alarm_ongoing_channel"
        private const val MAX_TRACKING_DURATION_MS = 4 * 60 * 60 * 1000L
        private const val DEFAULT_UNREPORTED_ACCURACY_METERS = 100f

        fun start(
            context: Context,
            stopName: String,
            lat: Double,
            lon: Double,
            radius: Double,
            voice: Boolean = false,
            stopId: String? = null
        ) {
            val intent = Intent(context, StopAlarmService::class.java).apply {
                putExtra(EXTRA_STOP_NAME, stopName)
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LON, lon)
                putExtra(EXTRA_RADIUS, radius)
                putExtra(EXTRA_VOICE, voice)
                putExtra(EXTRA_STOP_ID, stopId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, StopAlarmService::class.java))
            } catch (e: Exception) {
                // сервис мог быть уже остановлен — игнорируем
            }
        }
    }
}
