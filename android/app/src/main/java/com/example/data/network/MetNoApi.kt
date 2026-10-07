package com.example.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * MET Norway — бесплатный сервис погоды БЕЗ ключа (Норвежский метеоинститут).
 * Основной источник: api.met.no доступен из РФ, в отличие от Open-Meteo,
 * который у части операторов отбрасывает TCP-соединение.
 * Требует заголовок User-Agent (добавляется в OkHttp-интерсепторе).
 * Документация: https://api.met.no/weatherapi/locationforecast/2.0/documentation
 */
interface MetNoApi {
    @GET("weatherapi/locationforecast/2.0/compact")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): MetNoResponse
}
