package com.example.ui.map

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestRequestGuardTest {

    @Test
    fun supersededRequestIsRejected() = runTest {
        val guard = LatestRequestGuard()
        val oldRequest = guard.next()
        val currentRequest = guard.next()

        var rejected = false
        try {
            guard.ensureCurrent(oldRequest)
        } catch (_: CancellationException) {
            rejected = true
        }
        assertTrue(rejected)
        guard.ensureCurrent(currentRequest)
    }

    @Test
    fun cancelledNonCooperativeRequestCannotPublishStaleState() = runTest {
        val guard = LatestRequestGuard()
        val oldRequest = guard.next()
        var staleWrite = false
        var rejected = false

        val oldJob = launch {
            try {
                // Simulates a callback/API that finishes even after its caller was cancelled.
                withContext(NonCancellable) {
                    delay(100)
                    guard.ensureCurrent(oldRequest)
                    staleWrite = true
                }
            } catch (_: CancellationException) {
                rejected = true
            }
        }
        runCurrent()

        val currentRequest = guard.next()
        oldJob.cancel()
        advanceUntilIdle()
        oldJob.cancelAndJoin()

        guard.ensureCurrent(currentRequest)
        assertFalse(staleWrite)
        assertTrue(rejected)
    }

    @Test
    fun invalidationRejectsResultWithoutStartingAnotherRequest() = runTest {
        val guard = LatestRequestGuard()
        val request = guard.next()
        guard.invalidate()

        var rejected = false
        try {
            guard.ensureCurrent(request)
        } catch (_: CancellationException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun supersededOrdinaryFailureCannotPublishStaleErrorState() = runTest {
        val guard = LatestRequestGuard()
        val oldRequest = guard.next()
        var staleErrorWrite = false
        var rejected = false

        val oldJob = launch {
            withContext(NonCancellable) {
                delay(100)
                try {
                    throw IllegalStateException("late failure")
                } catch (_: Exception) {
                    try {
                        guard.ensureCurrent(oldRequest)
                        staleErrorWrite = true
                    } catch (_: CancellationException) {
                        rejected = true
                    }
                }
            }
        }
        runCurrent()

        guard.next()
        oldJob.cancel()
        advanceUntilIdle()
        oldJob.cancelAndJoin()

        assertFalse(staleErrorWrite)
        assertTrue(rejected)
    }
}
