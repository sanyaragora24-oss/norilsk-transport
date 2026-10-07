package com.example.data

import android.location.Location
import com.yandex.mapkit.geometry.Point

/**
 * Прикидывает время прибытия автобусов к остановке (в минутах) по живым позициям
 * автобусов и порядку остановок маршрута. Это ОЦЕНКА, не точное расписание.
 *
 * Логика: для каждого автобуса на маршруте находим ближайшую к нему остановку,
 * если она ДО целевой — считаем расстояние по цепочке остановок до целевой и делим
 * на среднюю скорость. Берём минимум среди всех подъезжающих автобусов.
 */
object ArrivalEstimator {

    // Средняя городская скорость автобуса, м/с (~18 км/ч).
    private const val AVG_SPEED_MPS = 5.0

    /** @return минуты до прибытия, либо null если нет подъезжающих автобусов. */
    fun estimateMinutes(route: Route, stopId: String, buses: List<Bus>): Int? {
        val targetIndex = route.stops.indexOfFirst { it.id == stopId }
        if (targetIndex < 0) return null

        val routeBuses = buses.filter { it.routeId == route.id }
        if (routeBuses.isEmpty()) return null

        var best: Int? = null
        for (bus in routeBuses) {
            val busIndex = nearestStopIndex(route, bus.location)
            if (busIndex < 0 || busIndex > targetIndex) continue // автобус уже проехал остановку
            val distance = alongRouteDistance(route, busIndex, targetIndex) +
                distanceMeters(bus.location, route.stops[busIndex].location)
            val minutes = Math.ceil(distance / AVG_SPEED_MPS / 60.0).toInt().coerceAtLeast(1)
            val current = best
            if (current == null || minutes < current) best = minutes
        }
        return best
    }

    private fun nearestStopIndex(route: Route, point: Point): Int {
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        route.stops.forEachIndexed { idx, stop ->
            val d = distanceMeters(point, stop.location)
            if (d < bestDist) {
                bestDist = d
                bestIdx = idx
            }
        }
        return bestIdx
    }

    private fun alongRouteDistance(route: Route, fromIdx: Int, toIdx: Int): Double {
        if (toIdx <= fromIdx) return 0.0
        var sum = 0.0
        for (i in fromIdx until toIdx) {
            sum += distanceMeters(route.stops[i].location, route.stops[i + 1].location)
        }
        return sum
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val res = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, res)
        return res[0].toDouble()
    }
}
