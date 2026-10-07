package com.example.data.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Погода для Норильска без API-ключа.
 *
 * Стратегия отказоустойчивости:
 *  1) основной источник — MET Norway (api.met.no), работает в РФ без VPN;
 *  2) запасной — Open-Meteo (api.open-meteo.com), может быть заблокирован
 *     у некоторых операторов; timeout укорочен до 3 с, чтобы не «зависать»;
 *  3) если оба недоступны — последнее загруженное значение из кэша
 *     («Нет подключения» показываем только если кэша ещё нет).
 *
 *  Координаты Норильска фиксированы, геолокация пользователя не используется.
 */
class WeatherRepository {

    private val lat = 69.3498
    private val lon = 88.2005

    // MET Norway: работает в РФ, стандартный таймаут
    private val metNoClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(Interceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "NorilskTransit/1.0 (norilsk-transport app)")
                .build()
            chain.proceed(req)
        })
        .build()

    // Open-Meteo: может быть заблокирован, короткий таймаут — быстрый fallback
    private val openMeteoClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val metNo = Retrofit.Builder()
        .baseUrl("https://api.met.no/")
        .client(metNoClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MetNoApi::class.java)

    private val openMeteo = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(openMeteoClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenMeteoApi::class.java)

    private var cachedWeather: WeatherUi? = null
    private var lastFetchTime: Long = 0

    suspend fun getNorilskWeather(force: Boolean = false): WeatherUi {
        val now = System.currentTimeMillis()
        // кэш на 10 минут
        if (!force && cachedWeather != null && now - lastFetchTime < 10 * 60 * 1000) {
            return cachedWeather!!
        }

        // 1) MET Norway (основной — работает в РФ)
        try {
            val ts = metNo.getForecast(lat, lon).properties.timeseries.firstOrNull()
            if (ts != null) {
                val d = ts.data.instant.details
                return cache(
                    WeatherUi(
                        temp = d.airTemperature,
                        feelsLike = d.airTemperature, // MET не отдаёт «ощущается»
                        description = metSymbolToDescription(ts.data.next1h?.summary?.symbolCode),
                        windSpeed = d.windSpeed,
                        cityName = "Норильск",
                        updateTime = now
                    ), now
                )
            }
        } catch (e: Exception) {
            Log.w("WeatherRepository", "MET Norway недоступен: ${e.message}")
        }

        // 2) Open-Meteo (запасной — может быть заблокирован)
        try {
            val response = openMeteo.getCurrentWeather(lat, lon)
            val r = response.current
            return cache(
                WeatherUi(
                    temp = r.temperature,
                    feelsLike = r.apparentTemperature,
                    description = weatherCodeToDescription(r.weatherCode),
                    windSpeed = r.windSpeed,
                    cityName = "Норильск",
                    updateTime = now,
                    windGusts = r.windGusts,
                    visibilityMeters = r.visibility,
                    hourly = response.hourly.toWeatherHours(r.time)
                ), now
            )
        } catch (e: Exception) {
            Log.w("WeatherRepository", "Open-Meteo недоступен: ${e.message}")
        }

        // 3) последнее известное значение, если есть
        cachedWeather?.let { return it }
        throw IllegalStateException("Погода недоступна (оба источника не отвечают)")
    }

    private fun cache(w: WeatherUi, time: Long): WeatherUi {
        cachedWeather = w
        lastFetchTime = time
        return w
    }
}
