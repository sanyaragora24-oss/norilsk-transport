package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopAlarmRequestTest {
    @Test
    fun normalNorilskStopIsValid() {
        assertTrue(StopAlarmRequest.isValid(69.3215728, 87.9627228, 350.0))
    }

    @Test
    fun invalidRequestsAreRejectedBeforeTheServiceStarts() {
        assertFalse(StopAlarmRequest.isValid(Double.NaN, 88.0, 350.0))
        assertFalse(StopAlarmRequest.isValid(69.3, Double.POSITIVE_INFINITY, 350.0))
        assertFalse(StopAlarmRequest.isValid(91.0, 88.0, 350.0))
        assertFalse(StopAlarmRequest.isValid(69.3, 181.0, 350.0))
        assertFalse(StopAlarmRequest.isValid(69.3, 88.0, 50.0))
        assertFalse(StopAlarmRequest.isValid(69.3, 88.0, 5_000.0))
        assertFalse(StopAlarmRequest.isValid(69.3, 88.0, Double.NaN))
    }
}
