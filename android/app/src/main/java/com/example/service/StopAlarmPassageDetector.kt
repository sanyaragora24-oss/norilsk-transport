package com.example.service

import kotlin.math.cos
import kotlin.math.hypot

/** Infers a missed stop fix only when a short, precise GPS segment crosses the stop. */
internal class StopAlarmPassageDetector(
    private val targetLatitude: Double,
    private val targetLongitude: Double,
    private val radiusMeters: Double = 40.0
) {
    private data class Fix(val x: Double, val y: Double, val timeMs: Long)
    private var previous: Fix? = null

    fun update(latitude: Double, longitude: Double, accuracyMeters: Float, timeMs: Long): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            !accuracyMeters.isFinite() || accuracyMeters !in 0f..75f || timeMs < 0L
        ) {
            previous = null
            return false
        }
        val current = Fix(
            (longitude - targetLongitude) * 111_320.0 * cos(Math.toRadians(targetLatitude)),
            (latitude - targetLatitude) * 111_320.0,
            timeMs
        )
        val before = previous
        previous = current
        if (before == null) return false
        val elapsedMs = current.timeMs - before.timeMs
        if (elapsedMs !in 1L..15_000L) return false
        val dx = current.x - before.x
        val dy = current.y - before.y
        val length = hypot(dx, dy)
        if (length < 1.0 || length / (elapsedMs / 1000.0) > 60.0) return false
        val projection = -(before.x * dx + before.y * dy) / (length * length)
        if (projection !in 0.0..1.0) return false
        return hypot(before.x + projection * dx, before.y + projection * dy) <= radiusMeters
    }
}
