package com.example.data

import com.example.data.local.dto.NorilskRoutesDto
import com.yandex.mapkit.geometry.Point
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
class RoutePlannerAssetIntegrationTest {

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
    fun restoredAssetsExcludeObsolete31BAndBuildDirect31Trip() {
        val routes = routes()
        assertEquals(61, routes.size)
        assertTrue(routes.none { it.id == "400202:1" || it.number == "31Б" })
        assertEquals(6, routes.count { it.number == "31" })
        assertEquals(6, routes.count { it.number == "31Э" })
        val direct31 = routes.single { it.id == "2246:0" }

        val journey = RoutePlanner.plan(listOf(direct31), direct31.stops.first(), direct31.stops.last())

        assertNotNull(journey)
        assertEquals(0, journey!!.transfers)
        assertTrue(journey.steps.any { it.type == RoutePlanner.StepType.RIDE && it.routeNumber == "31" })
        println("DIRECT_ASSET_SCENARIO=${direct31.stops.first().name} -> ${direct31.stops.last().name}")
    }

    @Test
    fun movingGpsOriginStillFindsTripFromLeninskyToFifthMicrodistrict() {
        val routes = routes()
        val to = routes.flatMap { it.stops }.first { it.id == "22492" }
        val journeys = RoutePlanner.planVariantsFromLocation(
            routes, Point(69.35650, 88.1878647), to
        )
        assertTrue("A nearby boarding stop must remain usable after GPS origin moves", journeys.isNotEmpty())
        assertTrue(journeys.all { it.steps.any { step -> step.type == RoutePlanner.StepType.RIDE } })
    }

    @Test
    fun gpsOriginOutsideWalkingDistanceDoesNotTeleportToBusStop() {
        val routes = routes()
        val to = routes.flatMap { it.stops }.first { it.id == "22492" }
        assertTrue(RoutePlanner.planVariantsFromLocation(routes, Point(68.0, 85.0), to).isEmpty())
    }

    @Test
    fun realAssetsBuildJourneyWithTransfer() {
        val routes = routes()
        val allStops = routes.flatMap { it.stops }
        val candidateIds = listOf("22388", "22421", "22457", "22442", "22449")
        val from = allStops.first { it.id == "22326" }
        val match = candidateIds.firstNotNullOfOrNull { id ->
            val to = allStops.first { it.id == id }
            RoutePlanner.planVariants(routes, from, to)
                .firstOrNull { it.transfers > 0 }
                ?.let { to to it }
        }
        val to = match?.first
        val journey = match?.second

        assertNotNull(journey)
        assertTrue(journey!!.steps.count { it.type == RoutePlanner.StepType.RIDE } >= 2)
        println(
            "TRANSFER_ASSET_SCENARIO=${from.name} -> ${to!!.name}; " +
                journey.steps.filter { it.type == RoutePlanner.StepType.RIDE }
                    .joinToString(" -> ") { it.routeNumber ?: "?" }
        )
    }
}
