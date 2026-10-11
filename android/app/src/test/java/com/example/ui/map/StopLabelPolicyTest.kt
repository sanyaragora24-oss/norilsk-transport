package com.example.ui.map

import com.example.data.Stop
import com.yandex.mapkit.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopLabelPolicyTest {
    // Плотная цепочка остановок, ~150 м друг от друга (как ЦБК — Стройбаза у 31).
    private val stops = (0 until 10).map { i ->
        Stop("s$i", "Остановка номер $i", Point(69.3200, 87.9600 + i * 0.004))
    }

    @Test
    fun overviewZoomLabelsOnlyTerminals() {
        // Длинный маршрут: конечные далеко друг от друга, промежуточных подписей нет.
        val longRoute = (0 until 10).map { i -> Stop("l$i", "Остановка $i", Point(69.32, 87.80 + i * 0.05)) }
        val labels = StopLabelPolicy.visibleLabelStopIds(longRoute, zoom = 11f)
        assertEquals(setOf("l0", "l9"), labels)
    }

    @Test
    fun overlappingTerminalsKeepOnlyTheFirstLabel() {
        // Конечные в ~50 dp друг от друга на обзорном масштабе — вторая подпись легла бы поверх первой.
        val labels = StopLabelPolicy.visibleLabelStopIds(stops, zoom = 11f)
        assertEquals(setOf("s0"), labels)
    }

    @Test
    fun denseStopsAtMidZoomDoNotGetOverlappingLabels() {
        val labels = StopLabelPolicy.visibleLabelStopIds(stops, zoom = 13f)
        assertTrue("first terminal has priority", "s0" in labels)
        assertTrue("dense chain must be thinned, got $labels", labels.size < stops.size)
    }

    @Test
    fun closeZoomLabelsEveryStop() {
        val labels = StopLabelPolicy.visibleLabelStopIds(stops, zoom = 18f)
        assertEquals(stops.map { it.id }.toSet(), labels)
    }

    @Test
    fun highlightedStopIsAlwaysLabelledEvenWhenCrowded() {
        val labels = StopLabelPolicy.visibleLabelStopIds(stops, zoom = 11f, highlightedStopId = "s4")
        assertTrue("s4" in labels)
    }

    @Test
    fun repeatedStopOfALoopRouteIsLabelledOnce() {
        val loop = stops + stops.first()
        val labels = StopLabelPolicy.visibleLabelStopIds(loop, zoom = 18f)
        assertEquals(stops.size, labels.size)
    }

    @Test
    fun zoomBucketIgnoresSmallGestureSteps() {
        assertEquals(StopLabelPolicy.zoomBucket(12.1f), StopLabelPolicy.zoomBucket(12.4f))
        assertTrue(StopLabelPolicy.zoomBucket(12.1f) != StopLabelPolicy.zoomBucket(12.6f))
    }

    @Test
    fun easternStopsAreLabelledOnTheLeftSoTerminalLabelsStayOnScreen() {
        val route = listOf(
            Stop("w", "Запад", Point(69.33, 87.80)),
            Stop("m", "Центр", Point(69.33, 88.00)),
            Stop("e", "Восток", Point(69.36, 88.17))
        )
        assertEquals(StopLabelPolicy.Side.RIGHT, StopLabelPolicy.labelSide(route[0], route))
        assertEquals(StopLabelPolicy.Side.LEFT, StopLabelPolicy.labelSide(route[2], route))
    }
}
