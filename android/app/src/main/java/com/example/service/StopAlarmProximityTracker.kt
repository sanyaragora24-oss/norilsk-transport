package com.example.service

/**
 * Deterministic proximity state machine used by [StopAlarmService].
 *
 * Keeping this logic independent from Android location APIs makes the two alarm
 * stages testable without a device or emulator.
 */
class StopAlarmProximityTracker(
    private val approachRadiusMeters: Double = 350.0,
    private val arrivalRadiusMeters: Double = 40.0,
    private val maxUsableAccuracyMeters: Float = 200f,
    private val maxTrackingDurationMs: Long = 4 * 60 * 60 * 1000L
) {
    enum class Event { APPROACH, ARRIVAL, TIMEOUT }

    private var approachAnnounced = false
    private var arrivalAnnounced = false
    private var timeoutAnnounced = false
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun update(
        distanceMeters: Double,
        accuracyMeters: Float,
        passedStopBetweenFixes: Boolean = false
    ): List<Event> {
        if (cancelled) return emptyList()
        if (!distanceMeters.isFinite() || distanceMeters < 0.0) return emptyList()
        if (!accuracyMeters.isFinite() || accuracyMeters < 0f) return emptyList()
        if (accuracyMeters > maxUsableAccuracyMeters) return emptyList()

        val events = mutableListOf<Event>()
        val conservativeDistance = (distanceMeters - accuracyMeters).coerceAtLeast(0.0)
        if (!approachAnnounced &&
            conservativeDistance <= approachRadiusMeters
        ) {
            approachAnnounced = true
            events += Event.APPROACH
        }

        if (!arrivalAnnounced &&
            accuracyMeters <= MAX_ARRIVAL_ACCURACY_METERS &&
            (distanceMeters <= arrivalRadiusMeters || passedStopBetweenFixes)
        ) {
            // A fast vehicle can move from outside 350 m directly into the arrival
            // radius. The update then emits both stages in their natural order.
            if (!approachAnnounced) {
                approachAnnounced = true
                events += Event.APPROACH
            }
            arrivalAnnounced = true
            events += Event.ARRIVAL
        }
        return events
    }

    fun onElapsed(elapsedMs: Long): List<Event> {
        if (cancelled || arrivalAnnounced || timeoutAnnounced || elapsedMs < maxTrackingDurationMs) {
            return emptyList()
        }
        timeoutAnnounced = true
        return listOf(Event.TIMEOUT)
    }

    private companion object {
        const val MAX_ARRIVAL_ACCURACY_METERS = 75f
    }
}
