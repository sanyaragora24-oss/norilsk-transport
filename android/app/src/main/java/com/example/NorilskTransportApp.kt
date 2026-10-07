package com.example

import android.app.Application
import com.example.data.local.StopAlarmNotifier
import com.example.data.local.WeatherAlertNotifier
import com.yandex.mapkit.MapKitFactory

class NorilskTransportApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // MapKit must be initialized once per process.
        // setApiKey must be called before initialize.
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
        MapKitFactory.initialize(this)

        // Каналы уведомлений создаём заранее: иначе push об ограничении движения,
        // пришедший до первого запуска экрана карты, не будет показан.
        StopAlarmNotifier.createChannel(this)
        WeatherAlertNotifier.createChannels(this)
    }
}
