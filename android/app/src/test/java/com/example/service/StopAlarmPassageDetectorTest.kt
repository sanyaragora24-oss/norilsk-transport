package com.example.service

import kotlin.math.cos
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopAlarmPassageDetectorTest {
    private val latitude = 69.35
    private val longitude = 88.2

    private fun StopAlarmPassageDetector.fix(x: Double, y: Double, time: Long, accuracy: Float = 10f) =
        update(latitude + y / 111_320.0,
            longitude + x / (111_320.0 * cos(Math.toRadians(latitude))), accuracy, time)

    @Test fun crossingStopBetweenAccurateFixesTriggersArrival() {
        val detector = StopAlarmPassageDetector(latitude, longitude)
        assertFalse(detector.fix(-120.0, 0.0, 0L))
        assertTrue(detector.fix(160.0, 0.0, 5_000L))
        val tracker = StopAlarmProximityTracker()
        org.junit.Assert.assertEquals(
            listOf(StopAlarmProximityTracker.Event.APPROACH, StopAlarmProximityTracker.Event.ARRIVAL),
            tracker.update(160.0, 10f, passedStopBetweenFixes = true)
        )
    }

    @Test fun movingAwayOnSameSideDoesNotCountAsPassage() {
        val detector = StopAlarmPassageDetector(latitude, longitude)
        assertFalse(detector.fix(120.0, 0.0, 0L))
        assertFalse(detector.fix(160.0, 0.0, 5_000L))
    }

    @Test fun parallelStreetDoesNotCountAsPassage() {
        val detector = StopAlarmPassageDetector(latitude, longitude)
        assertFalse(detector.fix(-120.0, 100.0, 0L))
        assertFalse(detector.fix(160.0, 100.0, 5_000L))
    }

    @Test fun inaccurateFixBreaksSegment() {
        val detector = StopAlarmPassageDetector(latitude, longitude)
        assertFalse(detector.fix(-120.0, 0.0, 0L))
        assertFalse(detector.fix(0.0, 0.0, 2_000L, 150f))
        assertFalse(detector.fix(120.0, 0.0, 4_000L))
    }

    @Test fun longGapsAndImpossibleSpeedDoNotCountAsPassage() {
        val detector = StopAlarmPassageDetector(latitude, longitude)
        assertFalse(detector.fix(-120.0, 0.0, 0L))
        assertFalse(detector.fix(160.0, 0.0, 16_000L))
        assertFalse(detector.fix(-120.0, 0.0, 17_000L))
    }

    @Test fun outOfOrderFixDoesNotCountAsPassage() {
        val detector = StopAlarmPassageDetector(latitude, longitude)
        assertFalse(detector.fix(-120.0, 0.0, 5_000L))
        assertFalse(detector.fix(160.0, 0.0, 4_000L))
    }
}
