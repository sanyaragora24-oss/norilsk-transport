package com.example.push

import com.example.data.local.WeatherAlertNotifier
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Приём официальных оповещений: штормовое предупреждение, ограничение движения
 * между районами, отмена рейсов. Такие решения принимает штаб «Шторм» / ЕДДС —
 * погодная автоматика внутри приложения о них знать не может, поэтому нужен
 * канал, через который город или перевозчик может сообщить об этом напрямую.
 *
 * Содержимое push-сообщения — недоверенные данные: его длина ограничивается, а
 * само сообщение может только показать уведомление. Никаких переходов по
 * ссылкам из payload и никаких действий с данными приложения по команде извне.
 */
class NorilskFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Токен намеренно не логируется: logcat читают другие процессы.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = (message.data[KEY_TITLE] ?: message.notification?.title)
            ?.trim()
            ?.take(MAX_TITLE_LENGTH)
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_TITLE

        val body = (message.data[KEY_BODY] ?: message.notification?.body)
            ?.trim()
            ?.take(MAX_BODY_LENGTH)
            ?.takeIf { it.isNotEmpty() }
            ?: return

        WeatherAlertNotifier.createChannels(applicationContext)
        WeatherAlertNotifier.notifyOfficial(applicationContext, title, body)
    }

    private companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val DEFAULT_TITLE = "Оповещение по транспорту"
        const val MAX_TITLE_LENGTH = 100
        const val MAX_BODY_LENGTH = 500
    }
}
