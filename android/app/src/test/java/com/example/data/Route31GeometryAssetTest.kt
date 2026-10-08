package com.example.data

import com.example.data.local.dto.NorilskRoutesDto
import com.yandex.mapkit.geometry.Point
import java.io.File
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Route31GeometryAssetTest {
    private fun routes(): List<Route> {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<NorilskRoutesDto>(
            File("src/main/assets/norilsk_routes.json").readText()
        )
        return dto.routes.map { route ->
            Route(
                id = route.id,
                number = route.number,
                origin = route.origin,
                destination = route.destination,
                color = route.colorArgb,
                stops = route.stops.map { Stop(it.id.toString(), it.name, Point(it.lat, it.lon)) },
                polyline = route.polyline.map { Point(it.lat, it.lon) }
            )
        }
    }

    @Test
    fun all31And31EVariantsKeepTheVerifiedAssetGeometry() {
        val target = routes().filter { it.number == "31" || it.number == "31Э" }
        val expectedIds = setOf(
            "2246:0", "2246:1", "2246:2", "2246:3", "2246:4", "2246:5",
            "3467:0", "3467:1", "3467:2", "3467:3", "3467:4", "3467:5"
        )

        assertEquals(expectedIds, target.map { it.id }.toSet())
        assertEquals(6, target.count { it.number == "31" })
        assertEquals(6, target.count { it.number == "31Э" })
        assertTrue(target.all(RouteGeometryPolicy::useVerifiedAssetGeometry))
    }

    @Test
    fun all31And31EPolylinesStayOnTheStopCorridorWithoutASouthernLoop() {
        routes().filter { it.number == "31" || it.number == "31Э" }.forEach { route ->
            val stopChainMeters = route.stops.zipWithNext().sumOf { (a, b) -> distanceMeters(a.location, b.location) }
            val polylineMeters = route.polyline.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }
            val southLimit = route.stops.minOf { it.location.latitude } - 0.004

            assertTrue("${route.id}: polyline must not leave the stop corridor southward", route.polyline.all {
                it.latitude >= southLimit
            })
            assertTrue("${route.id}: detour is too long for its stop chain", polylineMeters <= stopChainMeters * 1.5)
            assertTrue("${route.id}: a geometry edge is implausibly long", route.polyline.zipWithNext().all { (a, b) ->
                distanceMeters(a, b) < 800.0
            })
        }
    }

    @Test
    fun knownSouthernLoopWouldBeRejectedByTheCorridorRule() {
        val route = routes().first { it.id == "2246:3" }
        val southLimit = route.stops.minOf { it.location.latitude } - 0.004
        val knownBadMapKitPoint = Point(69.251142, 87.8)

        assertFalse(knownBadMapKitPoint.latitude >= southLimit)
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val latitudeScale = 111_000.0
        val northSouth = (a.latitude - b.latitude) * latitudeScale
        val eastWest = (a.longitude - b.longitude) * latitudeScale * cos(a.latitude * Math.PI / 180.0)
        return sqrt(northSouth * northSouth + eastWest * eastWest)
    }
}
