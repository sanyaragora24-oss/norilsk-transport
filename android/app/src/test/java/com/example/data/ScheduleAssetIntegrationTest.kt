package com.example.data

import com.example.data.local.dto.ScheduleFileDto
import kotlinx.serialization.json.Json
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAssetIntegrationTest {
    private fun snapshot() = Json { ignoreUnknownKeys = true }
        .decodeFromString<ScheduleFileDto>(File("src/main/assets/norilsk_schedule.json").readText())

    @Test fun verifiedSnapshotContainsAll62Directions() {
        val snapshot = snapshot()
        assertEquals("04.10.2026", snapshot.dataDate)
        assertEquals(62, snapshot.routes.size)
        snapshot.routes.values.filter { it.hasSchedule }.forEach { route ->
            assertTrue(route.timetable.any { it.weekday.isNotEmpty() || it.weekend.isNotEmpty() })
        }
    }

    @Test fun route31DeparturesRespectOfficialTalnakhskayaAndKomsomolskayaTags() {
        val routes = snapshot().routes
        assertTrue("05:55" in routes.getValue("2246:2").weekday)
        assertFalse("06:05" in routes.getValue("2246:2").weekday)
        assertTrue("06:05" in routes.getValue("2246:4").weekday)
        assertFalse("05:55" in routes.getValue("2246:4").weekday)
        assertTrue("08:10" in routes.getValue("2246:3").weekday)
        assertFalse("08:00" in routes.getValue("2246:3").weekday)
        assertTrue("08:00" in routes.getValue("2246:5").weekday)
        assertFalse("08:10" in routes.getValue("2246:5").weekday)
    }

    @Test fun dailyRoute23AlsoHasWeekendDepartures() {
        snapshot().routes.values.filter { it.number == "23" }.forEach { route ->
            assertTrue(route.weekday.isNotEmpty())
            assertEquals(route.weekday, route.weekend)
        }
    }

    @Test fun missingOfficialScheduleDoesNotBecomeInventedTimes() {
        val routes = snapshot().routes
        listOf("400201:0", "400201:1", "400202:1", "400203:0", "400203:1", "400204:1").forEach { id ->
            assertFalse(routes.getValue(id).hasSchedule)
            assertTrue(routes.getValue(id).weekday.isEmpty())
            assertTrue(routes.getValue(id).weekend.isEmpty())
        }
    }
}
