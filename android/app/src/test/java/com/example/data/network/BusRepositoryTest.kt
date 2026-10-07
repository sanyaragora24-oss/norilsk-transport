package com.example.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusRepositoryTest {

    @Test
    fun parsesValidVehicleFeedRowAndMapsRoute() {
        val repository = BusRepository("https://example.test/feed.csv") { name -> "route:$name" }

        val buses = repository.parseCsv(
            "123,2026-09-14 12:00:00,88.123331,69.815273,А123ВС,25,24,1"
        )

        assertEquals(1, buses.size)
        assertEquals("123", buses.single().id)
        assertEquals("route:25", buses.single().routeId)
        assertEquals(69.815273, buses.single().location.latitude, 0.000001)
        assertEquals(88.123331, buses.single().location.longitude, 0.000001)
        assertTrue(buses.single().isAccessible)
    }

    @Test
    fun skipsMalformedAndOutOfRangeRows() {
        val repository = BusRepository("https://example.test/feed.csv")

        val buses = repository.parseCsv(
            """
            broken
            1,time,88.0,91.0,plate,25
            2,time,181.0,69.0,plate,25
            3,time,88.0,69.0,plate,25,0,0
            """.trimIndent()
        )

        assertEquals(listOf("3"), buses.map { it.id })
        assertFalse(buses.single().isAccessible)
    }
}
