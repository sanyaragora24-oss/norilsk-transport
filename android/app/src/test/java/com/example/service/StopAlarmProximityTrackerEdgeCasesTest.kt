package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Test

class StopAlarmProximityTrackerEdgeCasesTest {

    @Test
    fun thresholdsAreInclusiveAndRepeatedFixesDoNotRepeatEvents() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(listOf(StopAlarmProximityTracker.Event.APPROACH), tracker.update(350.0, 100f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(350.0, 100f))
        assertEquals(listOf(StopAlarmProximityTracker.Event.ARRIVAL), tracker.update(40.0, 50f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(40.0, 50f))
    }

    @Test
    fun unusableAccuracyDoesNotConsumeEitherThreshold() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(300.0, 200.1f))
        assertEquals(listOf(StopAlarmProximityTracker.Event.APPROACH), tracker.update(300.0, 25f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(35.0, 200.1f))
        assertEquals(listOf(StopAlarmProximityTracker.Event.ARRIVAL), tracker.update(35.0, 25f))
    }

    @Test
    fun leavingAndReenteringDoesNotRepeatAnnouncements() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(listOf(StopAlarmProximityTracker.Event.APPROACH), tracker.update(340.0, 15f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(500.0, 15f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(300.0, 15f))
        assertEquals(listOf(StopAlarmProximityTracker.Event.ARRIVAL), tracker.update(30.0, 15f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(500.0, 15f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(30.0, 15f))
    }

    @Test
    fun cancellationSuppressesLateLocationCallbacks() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(listOf(StopAlarmProximityTracker.Event.APPROACH), tracker.update(300.0, 15f))
        tracker.cancel()
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(20.0, 10f))
    }

    @Test
    fun cancelledSessionStaysSilentWhileRestartedSessionCanAnnounceBothStages() {
        val cancelledSession = StopAlarmProximityTracker()
        cancelledSession.cancel()
        assertEquals(
            emptyList<StopAlarmProximityTracker.Event>(),
            cancelledSession.update(20.0, 10f)
        )

        val restartedSession = StopAlarmProximityTracker()
        assertEquals(
            listOf(StopAlarmProximityTracker.Event.APPROACH),
            restartedSession.update(349.0, 10f)
        )
        assertEquals(
            listOf(StopAlarmProximityTracker.Event.ARRIVAL),
            restartedSession.update(39.0, 10f)
        )
    }

    @Test
    fun invalidMeasurementsAreIgnoredWithoutConsumingState() {
        val tracker = StopAlarmProximityTracker()

        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(Double.NaN, 10f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(-1.0, 10f))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(20.0, Float.NaN))
        assertEquals(emptyList<StopAlarmProximityTracker.Event>(), tracker.update(20.0, -1f))
        assertEquals(
            listOf(StopAlarmProximityTracker.Event.APPROACH, StopAlarmProximityTracker.Event.ARRIVAL),
            tracker.update(20.0, 10f)
        )
    }
}
