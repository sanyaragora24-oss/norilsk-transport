package com.example.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplanPolicyTest {

    @Test
    fun gpsNoiseWithinHysteresisDoesNotSwitchBoardingStop() {
        assertFalse(
            AutoReplanPolicy.shouldReplan(
                currentDistanceMeters = 100.0,
                candidateDistanceMeters = 25.0,
                nowMs = 30_000L,
                lastReplanAtMs = 0L
            )
        )
    }

    @Test
    fun meaningfullyCloserStopSwitchesAfterCooldown() {
        assertTrue(
            AutoReplanPolicy.shouldReplan(
                currentDistanceMeters = 101.0,
                candidateDistanceMeters = 25.0,
                nowMs = 30_000L,
                lastReplanAtMs = 15_000L
            )
        )
    }

    @Test
    fun cooldownBlocksRapidRepeatedSwitch() {
        assertFalse(
            AutoReplanPolicy.shouldReplan(
                currentDistanceMeters = 200.0,
                candidateDistanceMeters = 10.0,
                nowMs = 29_999L,
                lastReplanAtMs = 15_000L
            )
        )
    }

    @Test
    fun farCurrentStopCanSwitchEvenWithoutSeventyFiveMeterAdvantage() {
        assertTrue(
            AutoReplanPolicy.shouldReplan(
                currentDistanceMeters = 351.0,
                candidateDistanceMeters = 340.0,
                nowMs = 15_000L,
                lastReplanAtMs = 0L
            )
        )
    }
}
