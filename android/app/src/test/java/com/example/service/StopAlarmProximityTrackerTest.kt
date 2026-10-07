package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Test

class StopAlarmProximityTrackerTest {

    @Test
    fun announcesApproachOnceAndArrivalOnce() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(500.0, 15f))
        assertEquals(listOf(StopAlarmProximityTracker.Event.APPROACH), tracker.update(349.0, 15f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(200.0, 15f))
        assertEquals(listOf(StopAlarmProximityTracker.Event.ARRIVAL), tracker.update(39.0, 15f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(10.0, 15f))
    }

    @Test
    fun poorAccuracyDoesNotAnnounceArrivalBeforeReachingStop() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(
            listOf(StopAlarmProximityTracker.Event.APPROACH),
            tracker.update(90.0, 80f)
        )
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(20.0, 150f))
        assertEquals(listOf(StopAlarmProximityTracker.Event.ARRIVAL), tracker.update(20.0, 10f))
    }

    @Test
    fun fastJumpEmitsBothStagesInOrder() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(
            listOf(
                StopAlarmProximityTracker.Event.APPROACH,
                StopAlarmProximityTracker.Event.ARRIVAL
            ),
            tracker.update(20.0, 10f)
        )
    }

    @Test
    fun movingAwayFromStopDoesNotProveArrival() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(
            listOf(StopAlarmProximityTracker.Event.APPROACH),
            tracker.update(120.0, 15f)
        )
        assertEquals(
            emptyList<StopAlarmProximityTracker.Event>(),
            tracker.update(160.0, 15f)
        )
    }

    @Test
    fun accuracyImprovementWithoutRealMovementDoesNotLookLikePassingStop() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(
            listOf(StopAlarmProximityTracker.Event.APPROACH),
            tracker.update(180.0, 100f)
        )
        assertEquals(
            emptyList<StopAlarmProximityTracker.Event>(),
            tracker.update(205.0, 10f)
        )
    }

    @Test
    fun elapsedSessionEmitsTimeoutOnlyOnce() {
        val tracker = StopAlarmProximityTracker()
        val elapsedMethod = tracker.javaClass.methods.singleOrNull {
            it.name == "onElapsed" && it.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType))
        }

        org.junit.Assert.assertNotNull("Tracker must expose an elapsed-time timeout event", elapsedMethod)
        val first = elapsedMethod!!.invoke(tracker, 4 * 60 * 60 * 1000L) as List<*>
        val second = elapsedMethod.invoke(tracker, 4 * 60 * 60 * 1000L + 1) as List<*>

        assertEquals(listOf("TIMEOUT"), first.map { it.toString() })
        assertEquals(emptyList<String>(), second.map { it.toString() })
    }
}
