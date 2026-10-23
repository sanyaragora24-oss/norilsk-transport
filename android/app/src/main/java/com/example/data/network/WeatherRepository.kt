package com.example.data.network

import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Погода для Норильска с быстрым резервным источником и кэшем. */
class WeatherRepository {

    companion object {
        internal const val MET_NO_USER_AGENT = "NorilskTransit/1.2.10 (nightdriveai.ru)"
        private const val TAG = "WeatherRepository"
    }

    private val lat = 69.3498
    private val lon = 88.2005

    // MET Norway отвечает с телефона; короткие таймауты не задерживают fallback.
    private val metNoClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(weatherDiagnosticInterceptor("met-no"))
        .build()

    // Open-Meteo остаётся резервом: на проверенном Redmi соединение тайм-аутится.
    private val openMeteoClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(weatherDiagnosticInterceptor("open-meteo"))
        .build()

    private var cachedWeather: WeatherUi? = null
    private var lastFetchTime: Long = 0

    suspend fun getNorilskWeather(force: Boolean = false): WeatherUi {
        val now = System.currentTimeMillis()
        if (!force && cachedWeather != null && now - lastFetchTime < 10 * 60 * 1000) {
            return cachedWeather!!
        }

        try {
            val weather = fetchMetNo(now)
            if (weather != null) return cache(weather, now, "met-no")
            logDebug("source=met-no result=empty")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("met-no", e)
        }

        try {
            val weather = fetchOpenMeteo(now)
            return cache(weather, now, "open-meteo")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("open-meteo", e)
        }

        cachedWeather?.let { return it }
        throw IllegalStateException("Погода недоступна (оба источника не отвечают)")
    }

    internal fun createMetNoRequest(): Request = Request.Builder()
        .url("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon")
        .header("User-Agent", MET_NO_USER_AGENT)
        .get()
        .build()

    internal fun createOpenMeteoRequest(): Request {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter(
                "current",
                "temperature_2m,apparent_temperature,wind_speed_10m,wind_gusts_10m,weather_code,visibility"
            )
            .addQueryParameter(
                "hourly",
                "temperature_2m,apparent_temperature,wind_speed_10m,wind_gusts_10m,weather_code,visibility"
            )
            .addQueryParameter("forecast_hours", "12")
            .addQueryParameter("wind_speed_unit", "ms")
            .addQueryParameter("timezone", "auto")
            .build()
        return Request.Builder().url(url).get().build()
    }

    private suspend fun fetchMetNo(now: Long): WeatherUi? = withContext(Dispatchers.IO) {
        metNoClient.newCall(createMetNoRequest()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("MET Norway HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("MET Norway empty body")
            parseMetNo(body, now)
        }
    }

    private suspend fun fetchOpenMeteo(now: Long): WeatherUi = withContext(Dispatchers.IO) {
        openMeteoClient.newCall(createOpenMeteoRequest()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Open-Meteo HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Open-Meteo empty body")
            parseOpenMeteo(body, now)
        }
    }

    private fun parseMetNo(body: String, now: Long): WeatherUi? {
        val timeseries = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
        if (timeseries.length() == 0) return null
        val data = timeseries.getJSONObject(0).getJSONObject("data")
        val details = data.getJSONObject("instant").getJSONObject("details")
        val temperature = details.getDouble("air_temperature")
        val summary = data.optJSONObject("next_1_hours")?.optJSONObject("summary")
        return WeatherUi(
            temp = temperature,
            feelsLike = temperature,
            description = metSymbolToDescription(summary?.optString("symbol_code")),
            windSpeed = details.getDouble("wind_speed"),
            cityName = "Норильск",
            updateTime = now
        )
    }

    private fun parseOpenMeteo(body: String, now: Long): WeatherUi {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val hourlyObject = root.optJSONObject("hourly")
        val hourly = parseHourly(hourlyObject, current.optString("time").ifBlank { null })
        return WeatherUi(
            temp = current.getDouble("temperature_2m"),
            feelsLike = current.getDouble("apparent_temperature"),
            description = weatherCodeToDescription(current.getInt("weather_code")),
            windSpeed = current.getDouble("wind_speed_10m"),
            cityName = "Норильск",
            updateTime = now,
            windGusts = current.optionalDouble("wind_gusts_10m"),
            visibilityMeters = current.optionalDouble("visibility"),
            hourly = hourly
        )
    }

    private fun parseHourly(hourly: JSONObject?, currentTime: String?): List<WeatherHour> {
        val times = hourly?.optJSONArray("time") ?: return emptyList()
        if (times.length() == 0) return emptyList()
        val start = if (currentTime == null) 0 else {
            (0 until times.length()).firstOrNull { times.optString(it) >= currentTime } ?: return emptyList()
        }
        fun number(field: String, index: Int): Double? = hourly
            ?.optJSONArray(field)
            ?.takeIf { index < it.length() && !it.isNull(index) }
            ?.optDouble(index)

        return (start + 1 until times.length()).map { index ->
            WeatherHour(
                hoursFromNow = index - start,
                temp = number("temperature_2m", index),
                feelsLike = number("apparent_temperature", index),
                windSpeed = number("wind_speed_10m", index),
                windGusts = number("wind_gusts_10m", index),
                description = hourly.optJSONArray("weather_code")
                    ?.takeIf { index < it.length() && !it.isNull(index) }
                    ?.optInt(index)
                    ?.let(::weatherCodeToDescription) ?: "—",
                visibilityMeters = number("visibility", index)
            )
        }
    }

    private fun JSONObject.optionalDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun cache(weather: WeatherUi, time: Long, source: String): WeatherUi {
        logDebug("source=$source result=weather_received city=${weather.cityName} tempC=${weather.temp}")
        cachedWeather = weather
        lastFetchTime = time
        return weather
    }

    private fun weatherDiagnosticInterceptor(source: String) = Interceptor { chain ->
        val started = SystemClock.elapsedRealtime()
        try {
            val response = chain.proceed(chain.request())
            logDebug("source=$source http=${response.code} durationMs=${SystemClock.elapsedRealtime() - started}")
            response
        } catch (error: Exception) {
            logDebug("source=$source errorType=${error.javaClass.simpleName} durationMs=${SystemClock.elapsedRealtime() - started}")
            throw error
        }
    }

    private fun logFailure(source: String, error: Exception) {
        if (BuildConfig.DEBUG) Log.w(TAG, "source=$source errorType=${error.javaClass.simpleName}")
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.println(Log.DEBUG, TAG, message)
    }
}
