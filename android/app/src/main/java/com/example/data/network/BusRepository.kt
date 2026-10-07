package com.example.data.network

import com.example.data.Bus
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

/**
 * Репозиторий живых позиций автобусов.
 *
 * Источник — внешний HTTPS-фид в настраиваемом CSV-формате. Приложение не
 * предполагает, что это официальный фид перевозчика. Одна строка = один автобус:
 *
 *   id_устройства, время, ДОЛГОТА, ШИРОТА, госномер, маршрут, скорость, колясочный
 *   12345678,2023-03-11 17:32:42,88.123331,69.815273,А123ВС,25,24,1
 *
 * Обрати внимание: в CSV сначала идёт ДОЛГОТА (lon), потом ШИРОТА (lat),
 * а Yandex Point(latitude, longitude) — наоборот. В коде это учтено.
 *
 * @param feedUrl URL CSV-фида (берётся из BuildConfig.VEHICLE_FEED_URL).
 * @param resolveRouteId функция: имя маршрута из фида -> внутренний routeId ("2220:0").
 */
class BusRepository(
    private val feedUrl: String,
    private val resolveRouteId: (routeName: String) -> String? = { it }
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Поток списков автобусов. Обновляется раз в [intervalMs] мс. Работает на
     * Dispatchers.IO, ошибки сети не валят поток. Пустой результат тоже публикуется,
     * чтобы на карте не оставались устаревшие позиции.
     */
    fun busUpdates(intervalMs: Long = 10_000L): Flow<List<Bus>> = flow {
        while (true) {
            val buses = runCatching { fetchOnce() }.getOrElse { emptyList() }
            emit(buses)
            delay(intervalMs.coerceAtLeast(MIN_POLL_INTERVAL_MS))
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchOnce(): List<Bus> {
        val url = feedUrl.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return emptyList()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body ?: return emptyList()
            if (body.contentLength() > MAX_FEED_BYTES) return emptyList()
            val source = body.source()
            source.request(MAX_FEED_BYTES + 1L)
            if (source.buffer.size > MAX_FEED_BYTES) return emptyList()
            return parseCsv(source.readUtf8())
        }
    }

    internal fun parseCsv(csv: String): List<Bus> {
        val result = ArrayList<Bus>()
        csv.lineSequence().take(MAX_VEHICLES).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val p = line.split(',')
            if (p.size < 6) return@forEach

            // p[2] = долгота (lon), p[3] = широта (lat)
            val lon = p[2].trim().toDoubleOrNull() ?: return@forEach
            val lat = p[3].trim().toDoubleOrNull() ?: return@forEach
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@forEach

            val deviceId = p[0].trim().take(MAX_FIELD_LENGTH)
            val routeName = p[5].trim().take(MAX_FIELD_LENGTH)
            if (deviceId.isBlank() || routeName.isBlank()) return@forEach
            val routeId = resolveRouteId(routeName) ?: routeName
            val plate = p.getOrNull(4)?.trim()?.take(MAX_FIELD_LENGTH)?.takeIf { it.isNotEmpty() }
            val isAccessible = p.getOrNull(7)?.trim() == "1" // поле «колясочный» из фида

            result.add(
                Bus(
                    id = deviceId,
                    routeId = routeId,
                    location = Point(lat, lon),
                    plate = plate,
                    isAccessible = isAccessible
                )
            )
        }
        return result
    }

    private companion object {
        const val MAX_FEED_BYTES = 1_000_000L
        const val MAX_VEHICLES = 5_000
        const val MAX_FIELD_LENGTH = 80
        const val MIN_POLL_INTERVAL_MS = 5_000L
    }
}
