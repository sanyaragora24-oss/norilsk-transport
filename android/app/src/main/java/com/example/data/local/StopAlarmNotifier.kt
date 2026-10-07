package com.example.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R

/**
 * Помощник для оповещения «приближаетесь к остановке».
 * Создаёт канал уведомлений и показывает уведомление со звуком и вибрацией,
 * когда пользователь подъезжает к выбранной остановке.
 */
object StopAlarmNotifier {

    private const val CHANNEL_ID = "stop_alarm_channel"
    private const val APPROACH_NOTIFICATION_ID = 4201
    private const val ARRIVAL_NOTIFICATION_ID = 4203

    /** Вызывать один раз при старте приложения (в Application.onCreate). */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Оповещение об остановке",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Сигнал, когда вы подъезжаете к выбранной остановке"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /** Показать уведомление о приближении к остановке. */
    fun notifyApproaching(context: Context, stopName: String, distanceMeters: Int) {
        notify(
            context = context,
            notificationId = APPROACH_NOTIFICATION_ID,
            title = "Скоро ваша остановка",
            text = "До «$stopName» около $distanceMeters м. Готовьтесь к выходу"
        )
    }

    /** Повторное оповещение непосредственно у выбранной остановки. */
    fun notifyArrived(context: Context, stopName: String) {
        notify(
            context = context,
            notificationId = ARRIVAL_NOTIFICATION_ID,
            title = "Ваша остановка",
            text = "Вы приехали к «$stopName». Пора выходить"
        )
    }

    private fun notify(context: Context, notificationId: Int, title: String, text: String) {
        // Если нет разрешения на уведомления (Android 13+) — тихо выходим, без падения.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (launchIntent != null) {
            android.app.PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stop_marker)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // звук + вибрация
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // нет разрешения — игнорируем
        }
    }
}
