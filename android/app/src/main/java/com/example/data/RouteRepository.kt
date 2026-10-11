package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.dto.NorilskRoutesDto
import com.yandex.mapkit.geometry.Point
import java.io.InputStream
import kotlinx.serialization.json.Json

/**
 * Офлайн-маршруты из assets/norilsk_routes.json (~820 КБ).
 *
 * Разбор тяжёлый, поэтому [getRoutes] нельзя вызывать с главного потока до
 * первой загрузки — MapViewModel делает это на Dispatchers.IO. Загрузка
 * потокобезопасна: ассет читается ровно один раз, даже если несколько потоков
 * попросили маршруты одновременно; дальше все получают готовый кэш.
 */
class RouteRepository(
    private val openAsset: (String) -> InputStream
) {
    constructor(context: Context) : this({ name -> context.applicationContext.assets.open(name) })

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val lock = Any()

    @Volatile
    private var loaded: Loaded? = null

    private class Loaded(
        val routes: List<Route>,
        val stopIndex: Map<String, List<RouteSummary>>,
        val allStops: List<Stop>
    )

    fun getRoutes(): List<Route> = ensureLoaded().routes

    fun getRoutesForStop(stopId: String): List<RouteSummary> =
        ensureLoaded().stopIndex[stopId]
            ?.sortedWith { left, right ->
                compareRouteNumbers(left.number, right.number)
                    .takeIf { it != 0 }
                    ?: left.id.compareTo(right.id)
            }
            ?: emptyList()

    fun getAllStops(): List<Stop> = ensureLoaded().allStops

    private fun ensureLoaded(): Loaded {
        loaded?.let { return it }
        return synchronized(lock) {
            loaded ?: load().also { loaded = it }
        }
    }

    private fun load(): Loaded = try {
        val text = openAsset(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val dto = json.decodeFromString<NorilskRoutesDto>(text)
        val routes = dto.routes.map { routeDto ->
            Route(
                id = routeDto.id,
                number = routeDto.number,
                origin = routeDto.origin,
                destination = routeDto.destination,
                color = routeDto.colorArgb,
                stops = routeDto.stops.map { stopDto ->
                    Stop(
                        id = stopDto.id.toString(),
                        name = stopDto.name,
                        location = Point(stopDto.lat, stopDto.lon)
                    )
                },
                polyline = routeDto.polyline.map { Point(it.lat, it.lon) }
            )
        }.sortedWith(naturalRouteComparator)
        Loaded(
            routes = routes,
            stopIndex = buildStopIndex(routes),
            allStops = routes.flatMap { it.stops }
                .distinctBy { it.id }
                .sortedBy { normalizeTransitSearch(it.name) }
        )
    } catch (e: Exception) {
        // Битый ассет — это ошибка сборки; без маршрутов приложение бесполезно,
        // но падать на старте хуже: карта и погода продолжают работать.
        Log.e(TAG, "Failed to load $ASSET_NAME", e)
        Loaded(emptyList(), emptyMap(), emptyList())
    }

    private fun buildStopIndex(routes: List<Route>): Map<String, List<RouteSummary>> {
        val index = mutableMapOf<String, MutableList<RouteSummary>>()
        routes.forEach { route ->
            val summary = RouteSummary(
                id = route.id,
                number = route.number,
                origin = route.origin,
                destination = route.destination,
                color = route.color,
                intervalDescription = "Интервал 10-20 мин (офлайн данные)"
            )
            route.stops.forEach { stop ->
                val list = index.getOrPut(stop.id) { mutableListOf() }
                if (list.none { it.id == route.id }) {
                    list.add(summary)
                }
            }
        }
        return index
    }

    private companion object {
        const val ASSET_NAME = "norilsk_routes.json"
        const val TAG = "RouteRepository"
    }
}
