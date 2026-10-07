package com.example.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.data.network.WeatherAlert
import com.example.data.network.WeatherAlertLevel

/**
 * Уведомления о непогоде.
 *
 * Два канала, чтобы пассажир мог отключить бытовые предупреждения, но оставить
 * штормовые: мороз −30 °C в Норильске — обычное дело, а закрытие дороги на
 * Талнах — нет.
 *
 * Одно и то же предупреждение не показывается повторно, пока не истечёт
 * [REPEAT_TIMEOUT_MS] или пока погода не сменит категорию: человек, который
 * уже знает про пургу, не должен получать её каждые десять минут.
 */
object WeatherAlertNotifier {

    const val CHANNEL_STORM = "weather_storm_channel"
    const val CHANNEL_ROUTINE = "weather_routine_channel"
    const val CHANNEL_OFFICIAL_STORM = "official_storm_push_channel_v1"

    private const val PREFS = "weather_alerts"
    private const val REPEAT_TIMEOUT_MS = 6 * 60 * 60 * 1000L // 6 часов

    private const val STORM_NOTIFICATION_ID = 4301
    private const val ROUTINE_NOTIFICATION_ID = 4302
    private const val OFFICIAL_NOTIFICATION_ID = 4303

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STORM,
                "Штормовые предупреждения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Пурга, аномальный мороз, ограничение движения между районами"
                enableVibration(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ROUTINE,
                "Погода на маршруте",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Метель, гололёд, сильный ветер — влияет на время в пути"
                setShowBadge(false)
            }
        )

        // FCM data messages and notification messages delivered by Android both
        // use this dedicated channel. They must not overwrite locally calculated
        // weather warnings or inherit that channel's user preference.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OFFICIAL_STORM,
                "Официальные штормовые оповещения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ограничения движения и отмены рейсов от перевозчика или ЕДДС"
                enableVibration(true)
            }
        )
    }

    /**
     * Показывает самое важное из предупреждений, если о нём ещё не сообщали.
     *
     * @return true, если уведомление было показано.
     */
    fun notifyIfNeeded(context: Context, alerts: List<WeatherAlert>): Boolean {
        val alert = alerts.firstOrNull { it.level != WeatherAlertLevel.ADVISORY } ?: return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastShown = prefs.getLong(alert.id, 0L)
        if (now - lastShown < REPEAT_TIMEOUT_MS) return false

        val severe = alert.level == WeatherAlertLevel.SEVERE
        val channel = if (severe) CHANNEL_STORM else CHANNEL_ROUTINE
        val notificationId = if (severe) STORM_NOTIFICATION_ID else ROUTINE_NOTIFICATION_ID

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val text = "${alert.message} ${alert.advice}"
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stop_marker)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (severe) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
        if (severe) builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        if (contentIntent != null) builder.setContentIntent(contentIntent)

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            prefs.edit().putLong(alert.id, now).apply()
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Официальное оповещение, пришедшее push-сообщением (штаб «Шторм», ЕДДС,
     * перевозчик). Такие сообщения показываем всегда: их присылают редко и
     * только по делу.
     */
    fun notifyOfficial(context: Context, title: String, body: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_OFFICIAL_STORM)
            .setSmallIcon(R.drawable.ic_stop_marker)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
        if (contentIntent != null) builder.setContentIntent(contentIntent)

        try {
            NotificationManagerCompat.from(context).notify(OFFICIAL_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // нет разрешения — молча выходим
        }
    }
}
