package com.example.ui.map

/** Pure decision rule for switching the journey's GPS-derived boarding stop. */
internal object AutoReplanPolicy {
    const val HYSTERESIS_METERS = 75.0
    const val MAX_START_DISTANCE_METERS = 350.0
    const val COOLDOWN_MS = 15_000L

    fun shouldReplan(
        currentDistanceMeters: Double,
        candidateDistanceMeters: Double,
        nowMs: Long,
        lastReplanAtMs: Long
    ): Boolean {
        val meaningfullyCloser =
            candidateDistanceMeters + HYSTERESIS_METERS < currentDistanceMeters
        val currentIsFar = currentDistanceMeters > MAX_START_DISTANCE_METERS
        val cooldownElapsed = nowMs - lastReplanAtMs >= COOLDOWN_MS
        return (meaningfullyCloser || currentIsFar) && cooldownElapsed
    }
}
