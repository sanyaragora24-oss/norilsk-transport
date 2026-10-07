package com.example.ui.map

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Prevents a superseded coroutine from publishing a late result even when the
 * underlying API does not react to cancellation immediately.
 */
internal class LatestRequestGuard {
    private val generation = AtomicLong(0L)

    fun next(): Long = generation.incrementAndGet()

    fun invalidate() {
        generation.incrementAndGet()
    }

    suspend fun ensureCurrent(requestId: Long) {
        currentCoroutineContext().ensureActive()
        if (generation.get() != requestId) {
            throw CancellationException("Request was superseded")
        }
    }
}
