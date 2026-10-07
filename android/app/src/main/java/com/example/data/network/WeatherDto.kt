package com.example.data.network

import com.google.gson.annotations.SerializedName

// --- Open-Meteo ответ ---
data class OpenMeteoResponse(
    @SerializedName("current") val current: OpenMeteoCurrent,
    @SerializedName("hourly") val hourly: OpenMeteoHourly? = null
)

data class OpenMeteoCurrent(
    @SerializedName("time") val time: String? = null,
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("apparent_temperature") val apparentTemperature: Double,
    @SerializedName("wind_speed_10m") val windSpeed: Double,
    @SerializedName("wind_gusts_10m") val windGusts: Double? = null,
    @SerializedName("weather_code") val weatherCode: Int,
    /** Видимость в метрах. Open-Meteo отдаёт её не для всех моделей. */
    @SerializedName("visibility") val visibility: Double? = null
)

/** Почасовой прогноз: нужен, чтобы предупредить об ухудшении заранее. */
data class OpenMeteoHourly(
    @SerializedName("time") val time: List<String>? = null,
    @SerializedName("temperature_2m") val temperature: List<Double?>? = null,
    @SerializedName("apparent_temperature") val apparentTemperature: List<Double?>? = null,
    @SerializedName("wind_speed_10m") val windSpeed: List<Double?>? = null,
    @SerializedName("wind_gusts_10m") val windGusts: List<Double?>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int?>? = null,
    @SerializedName("visibility") val visibility: List<Double?>? = null
)

// --- MET Norway (api.met.no) ответ — запасной источник, хостится в Норвегии ---
data class MetNoResponse(
    @SerializedName("properties") val properties: MetNoProperties
)
data class MetNoProperties(
    @SerializedName("timeseries") val timeseries: List<MetNoTimeseries>
)
data class MetNoTimeseries(
    @SerializedName("data") val data: MetNoData
)
data class MetNoData(
    @SerializedName("instant") val instant: MetNoInstant,
    @SerializedName("next_1_hours") val next1h: MetNoNext? = null
)
data class MetNoInstant(
    @SerializedName("details") val details: MetNoDetails
)
data class MetNoDetails(
    @SerializedName("air_temperature") val airTemperature: Double,
    @SerializedName("wind_speed") val windSpeed: Double
)
data class MetNoNext(
    @SerializedName("summary") val summary: MetNoSummary? = null
)
data class MetNoSummary(
    @SerializedName("symbol_code") val symbolCode: String? = null
)

/** MET Norway symbol_code → краткое описание на русском. */
fun metSymbolToDescription(symbol: String?): String {
    val s = symbol?.substringBefore("_") ?: return "—"
    return when {
        s.startsWith("clearsky") -> "Ясно"
        s.startsWith("fair") -> "Преимущественно ясно"
        s.startsWith("partlycloudy") -> "Переменная облачность"
        s.startsWith("cloudy") -> "Пасмурно"
        s.contains("fog") -> "Туман"
        s.contains("sleet") -> "Мокрый снег"
        s.contains("snow") -> "Снег"
        s.contains("rainshowers") || s.contains("heavyrain") -> "Ливень"
        s.contains("rain") -> "Дождь"
        s.contains("thunder") -> "Гроза"
        else -> "—"
    }
}

data class WeatherUi(
    val temp: Double,
    val feelsLike: Double,
    val description: String,
    val windSpeed: Double,
    val cityName: String,
    val updateTime: Long,
    /** Порыв ветра, м/с. null — источник не отдал. */
    val windGusts: Double? = null,
    /** Видимость, м. null — источник не отдал. */
    val visibilityMeters: Double? = null,
    /** Прогноз на ближайшие часы — для предупреждений «заранее». */
    val hourly: List<WeatherHour> = emptyList()
)

/**
 * Превращает почасовой блок Open-Meteo в список точек прогноза относительно
 * текущего часа.
 *
 * Время Open-Meteo отдаёт в ISO-8601 без смещения (`2026-09-20T14:00`), в
 * таймзоне, запрошенной параметром `timezone`. Такие строки сравнимы
 * лексикографически, поэтому искать текущий час через разбор даты не нужно.
 */
fun OpenMeteoHourly?.toWeatherHours(currentTime: String?): List<WeatherHour> {
    val hourly = this ?: return emptyList()
    val times = hourly.time ?: return emptyList()
    if (times.isEmpty()) return emptyList()

    // Индекс часа, с которого начинается будущее.
    val startIndex = when {
        currentTime == null -> 0
        else -> times.indexOfFirst { it >= currentTime }.takeIf { it >= 0 } ?: return emptyList()
    }

    fun <T> List<T?>?.at(i: Int): T? = this?.getOrNull(i)

    return times.indices
        .filter { it > startIndex }
        .map { i ->
            WeatherHour(
                hoursFromNow = i - startIndex,
                temp = hourly.temperature.at(i),
                feelsLike = hourly.apparentTemperature.at(i),
                windSpeed = hourly.windSpeed.at(i),
                windGusts = hourly.windGusts.at(i),
                description = hourly.weatherCode.at(i)?.let { weatherCodeToDescription(it) } ?: "—",
                visibilityMeters = hourly.visibility.at(i)
            )
        }
}

/** Одна точка почасового прогноза. */
data class WeatherHour(
    val hoursFromNow: Int,
    val temp: Double?,
    val feelsLike: Double?,
    val windSpeed: Double?,
    val windGusts: Double?,
    val description: String,
    val visibilityMeters: Double?
)

/** WMO weather code → краткое описание на русском. */
fun weatherCodeToDescription(code: Int): String = when (code) {
    0 -> "Ясно"
    1 -> "Преимущественно ясно"
    2 -> "Переменная облачность"
    3 -> "Пасмурно"
    45, 48 -> "Туман"
    51, 53, 55 -> "Морось"
    56, 57 -> "Ледяная морось"
    61, 63, 65 -> "Дождь"
    66, 67 -> "Ледяной дождь"
    71, 73, 75 -> "Снег"
    77 -> "Снежная крупа"
    80, 81, 82 -> "Ливень"
    85, 86 -> "Снегопад"
    95 -> "Гроза"
    96, 99 -> "Гроза с градом"
    else -> "—"
}
