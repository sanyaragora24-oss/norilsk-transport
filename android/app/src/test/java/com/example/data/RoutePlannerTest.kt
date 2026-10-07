package com.example.data

import com.yandex.mapkit.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {

    private fun stop(id: String, name: String, lat: Double, lon: Double) =
        Stop(id, name, Point(lat, lon))

    @Test
    fun buildsDirectTrip() {
        val a = stop("a", "А", 69.3500, 88.2000)
        val b = stop("b", "Б", 69.3550, 88.2100)
        val route = Route("r1", "1", "А", "Б", 0xFF0000, listOf(a, b))

        val journey = RoutePlanner.plan(listOf(route), a, b)

        assertNotNull(journey)
        assertEquals(0, journey!!.transfers)
        assertEquals(listOf("1"), journey.steps.filter { it.type == RoutePlanner.StepType.RIDE }.map { it.routeNumber })
    }

    @Test
    fun buildsTripWithTransferAtSameNamedStop() {
        val a = stop("a", "А", 69.3500, 88.2000)
        val x1 = stop("x1", "Пересадка", 69.3550, 88.2100)
        val x2 = stop("x2", "Пересадка", 69.3551, 88.2101)
        val b = stop("b", "Б", 69.3600, 88.2200)
        val routes = listOf(
            Route("r1", "1", "А", "Пересадка", 0xFF0000, listOf(a, x1)),
            Route("r2", "2", "Пересадка", "Б", 0x00FF00, listOf(x2, b))
        )

        val journey = RoutePlanner.plan(routes, a, b)

        assertNotNull(journey)
        assertEquals(1, journey!!.transfers)
        assertEquals(listOf("1", "2"), journey.steps.filter { it.type == RoutePlanner.StepType.RIDE }.map { it.routeNumber })
        assertTrue(journey.totalMinutes > 0)
    }
}
