package com.example.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationChannelsTest {

    @Test
    fun officialPushDoesNotShareLocalWeatherChannel() {
        assertEquals("official_storm_push_channel_v1", WeatherAlertNotifier.CHANNEL_OFFICIAL_STORM)
        assertNotEquals(WeatherAlertNotifier.CHANNEL_STORM, WeatherAlertNotifier.CHANNEL_OFFICIAL_STORM)
        assertNotEquals(WeatherAlertNotifier.CHANNEL_ROUTINE, WeatherAlertNotifier.CHANNEL_OFFICIAL_STORM)
    }
}
