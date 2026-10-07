package com.example.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo — бесплатный сервис погоды БЕЗ API-ключа.
 * Работает в РФ без VPN (api.open-meteo.com), в отличие от OpenWeather.
 * Документация: https://open-meteo.com/en/docs
 */
interface OpenMeteoApi {
    /**
     * Текущая погода + почасовой прогноз на ближайшие сутки.
     *
     * Порывы ветра и видимость нужны для штормовых предупреждений: пурга в
     * Норильске опознаётся именно по порыву и падению видимости, а не по
     * средней скорости ветра. Прогноз на ближайшие часы позволяет предупредить
     * пассажира заранее, а не в тот момент, когда он уже стоит на остановке.
     */
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String =
            "temperature_2m,apparent_temperature,wind_speed_10m,wind_gusts_10m," +
                "weather_code,visibility",
        @Query("hourly") hourly: String =
            "temperature_2m,apparent_temperature,wind_speed_10m,wind_gusts_10m," +
                "weather_code,visibility",
        @Query("forecast_hours") forecastHours: Int = 12,
        @Query("wind_speed_unit") windSpeedUnit: String = "ms",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse
}
