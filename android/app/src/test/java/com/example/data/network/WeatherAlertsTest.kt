package com.example.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAlertsTest {

    private fun weather(
        temp: Double = -20.0,
        feelsLike: Double = -25.0,
        description: String = "Ясно",
        wind: Double = 3.0,
        gusts: Double? = null,
        visibility: Double? = null,
        hourly: List<WeatherHour> = emptyList()
    ) = WeatherUi(
        temp = temp,
        feelsLike = feelsLike,
        description = description,
        windSpeed = wind,
        cityName = "Норильск",
        updateTime = 0L,
        windGusts = gusts,
        visibilityMeters = visibility,
        hourly = hourly
    )

    @Test
    fun `спокойная погода не даёт предупреждений`() {
        val alerts = evaluateWeatherAlerts(
            weather(temp = -10.0, feelsLike = -12.0, wind = 2.0)
        )
        assertTrue("Ожидался пустой список, получено: $alerts", alerts.isEmpty())
    }

    @Test
    fun `пурга с низкой видимостью — опасное явление с предупреждением о дорогах`() {
        val alerts = evaluateWeatherAlerts(
            weather(temp = -25.0, feelsLike = -38.0, description = "Снег", wind = 18.0, visibility = 300.0)
        )
        val blizzard = alerts.first { it.kind == WeatherAlertKind.BLIZZARD }
        assertEquals(WeatherAlertLevel.SEVERE, blizzard.level)
        assertTrue(
            "Пассажира нужно предупредить о закрытии дорог между районами",
            blizzard.advice.contains("Талнах")
        )
    }

    @Test
    fun `метель без критичной видимости — это предупреждение, а не опасное явление`() {
        val alerts = evaluateWeatherAlerts(
            weather(temp = -15.0, feelsLike = -22.0, description = "Снег", wind = 16.0, visibility = 4000.0)
        )
        val blizzard = alerts.first { it.kind == WeatherAlertKind.BLIZZARD }
        assertEquals(WeatherAlertLevel.WARNING, blizzard.level)
    }

    @Test
    fun `аномальный мороз — опасное явление`() {
        val alerts = evaluateWeatherAlerts(weather(temp = -42.0, feelsLike = -47.0))
        val frost = alerts.first { it.kind == WeatherAlertKind.FROST }
        assertEquals(WeatherAlertLevel.SEVERE, frost.level)
    }

    @Test
    fun `порыв ветра учитывается наравне со средней скоростью`() {
        val alerts = evaluateWeatherAlerts(
            weather(temp = -5.0, feelsLike = -10.0, wind = 9.0, gusts = 26.0)
        )
        val wind = alerts.first { it.kind == WeatherAlertKind.WIND }
        assertEquals(WeatherAlertLevel.SEVERE, wind.level)
    }

    @Test
    fun `гололёд определяется по температуре около нуля с осадками`() {
        val alerts = evaluateWeatherAlerts(
            weather(temp = -1.0, feelsLike = -4.0, description = "Дождь", wind = 3.0)
        )
        assertTrue(alerts.any { it.kind == WeatherAlertKind.ICE })
    }

    @Test
    fun `по каждому явлению остаётся только одно предупреждение`() {
        val alerts = evaluateWeatherAlerts(
            weather(
                temp = -30.0, feelsLike = -46.0, description = "Снег", wind = 20.0, visibility = 200.0,
                hourly = listOf(
                    hour(1, temp = -31.0, feels = -47.0, wind = 22.0, desc = "Снег", visibility = 150.0),
                    hour(2, temp = -32.0, feels = -48.0, wind = 24.0, desc = "Снег", visibility = 150.0)
                )
            )
        )
        assertEquals(
            "Каждое явление должно быть представлено один раз",
            alerts.size, alerts.map { it.kind }.distinct().size
        )
    }

    @Test
    fun `прогноз предупреждает заранее, когда сейчас спокойно`() {
        val alerts = evaluateWeatherAlerts(
            weather(
                temp = -10.0, feelsLike = -14.0, wind = 3.0,
                hourly = listOf(
                    hour(1, temp = -12.0, feels = -18.0, wind = 5.0, desc = "Пасмурно", visibility = 10000.0),
                    hour(3, temp = -20.0, feels = -30.0, wind = 20.0, desc = "Снег", visibility = 300.0)
                )
            )
        )
        val blizzard = alerts.first { it.kind == WeatherAlertKind.BLIZZARD }
        assertEquals(WeatherAlertLevel.SEVERE, blizzard.level)
        assertEquals(3, blizzard.hoursAhead)
        assertTrue(blizzard.isForecast)
        assertTrue("Заголовок должен говорить об ожидании", blizzard.title.contains("Ожидается"))
    }

    @Test
    fun `прогноз не дублирует то, что уже происходит`() {
        val alerts = evaluateWeatherAlerts(
            weather(
                temp = -25.0, feelsLike = -35.0, description = "Снег", wind = 20.0, visibility = 300.0,
                hourly = listOf(
                    hour(2, temp = -25.0, feels = -35.0, wind = 20.0, desc = "Снег", visibility = 300.0)
                )
            )
        )
        val blizzards = alerts.filter { it.kind == WeatherAlertKind.BLIZZARD }
        assertEquals(1, blizzards.size)
        assertEquals("Явление уже идёт — это не прогноз", 0, blizzards.first().hoursAhead)
    }

    @Test
    fun `самое серьёзное предупреждение идёт первым`() {
        val alerts = evaluateWeatherAlerts(
            weather(temp = -30.0, feelsLike = -46.0, description = "Снег", wind = 12.0, visibility = 5000.0)
        )
        assertEquals(WeatherAlertLevel.SEVERE, alerts.first().level)
    }

    @Test
    fun `отсутствие данных о видимости и порывах не ломает оценку`() {
        val alerts = evaluateWeatherAlerts(
            weather(temp = -18.0, feelsLike = -28.0, description = "Снег", wind = 16.0)
        )
        assertTrue(alerts.any { it.kind == WeatherAlertKind.BLIZZARD })
    }

    private fun hour(
        hoursFromNow: Int,
        temp: Double,
        feels: Double,
        wind: Double,
        desc: String,
        visibility: Double?
    ) = WeatherHour(
        hoursFromNow = hoursFromNow,
        temp = temp,
        feelsLike = feels,
        windSpeed = wind,
        windGusts = null,
        description = desc,
        visibilityMeters = visibility
    )
}
