package com.example.data

import android.content.Context
import com.example.data.local.dto.NorilskRoutesDto
import com.yandex.mapkit.geometry.Point
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

class RouteRepository(private val context: Context) {

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    private var cachedRoutes: List<Route>? = null
    private var stopIndex: Map<String, List<RouteSummary>>? = null
    private var allStops: List<Stop>? = null

    fun getRoutes(): List<Route> {
        cachedRoutes?.let { return it }

        return try {
            val inputStream = context.assets.open("norilsk_routes.json")
            val reader = InputStreamReader(inputStream)
            val dto = json.decodeFromString<NorilskRoutesDto>(reader.readText())
            
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
            cachedRoutes = routes
            buildStopIndex(routes)
            allStops = routes.flatMap { it.stops }
                .distinctBy { it.id }
                .sortedBy { normalizeTransitSearch(it.name) }
            routes
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun buildStopIndex(routes: List<Route>) {
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
        stopIndex = index
    }

    fun getRoutesForStop(stopId: String): List<RouteSummary> {
        if (stopIndex == null) {
            getRoutes() // Ensure index is built
        }
        return stopIndex?.get(stopId)
            ?.sortedWith { left, right ->
                compareRouteNumbers(left.number, right.number)
                    .takeIf { it != 0 }
                    ?: left.id.compareTo(right.id)
            }
            ?: emptyList()
    }

    fun getAllStops(): List<Stop> {
        if (allStops == null) {
            getRoutes()
        }
        return allStops ?: emptyList()
    }
}
