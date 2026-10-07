package com.example.data

import com.yandex.mapkit.geometry.Point

data class Route(
    val id: String,
    val number: String,
    val origin: String,
    val destination: String,
    val color: Long,
    val stops: List<Stop> = emptyList(),
    val polyline: List<Point> = emptyList()
)

data class Stop(
    val id: String,
    val name: String,
    val location: Point
)

data class Bus(
    val id: String,
    val routeId: String,
    val location: Point,
    val plate: String? = null,      // госномер из фида
    val isAccessible: Boolean = false // низкопольный / для колясок
)

private const val ROUTE_NUMBER_PREFIX = "number:"

/** Stable key for feeds that provide only a public route number, not a direction/variant id. */
fun routeNumberKey(number: String): String =
    ROUTE_NUMBER_PREFIX + number.trim().lowercase().replace("ё", "е")

/** Number-only feed data is safe for marker filtering, but not for directional ETA calculation. */
fun Bus.matchesRouteForDisplay(route: Route): Boolean =
    routeId == route.id || routeId == routeNumberKey(route.number)

/**
 * Summary of a route passing through a specific stop.
 */
data class RouteSummary(
    val id: String,
    val number: String,
    val origin: String,
    val destination: String,
    val color: Long,
    val intervalDescription: String? = null
)
