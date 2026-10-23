package com.example.ui.map

import com.example.data.Stop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Какие подписи остановок выбранного маршрута показывать на текущем масштабе.
 *
 * Раньше подписывалась каждая остановка, и на обзорном масштабе маршрута 31
 * названия ложились друг на друга сплошной кашей. Теперь подписи отбираются
 * по приоритету (выделенная остановка, конечные, затем по порядку следования)
 * и только если прямоугольник подписи не пересекает уже принятые. Остановки
 * без подписи всё равно рисуются и нажимаются.
 *
 * Координаты переводятся в экранные dp через Web Mercator: на масштабе z мир
 * занимает 256·2^z dp, как и в тайловой сетке MapKit.
 */
object StopLabelPolicy {
    /** Ниже этого масштаба подписываем только конечные и выделенную остановку. */
    const val OVERVIEW_ZOOM = 12.0f

    /** Размер шрифта подписи остановки в MapKit TextStyle. */
    const val LABEL_TEXT_SIZE = 11f
    const val HIGHLIGHTED_TEXT_SIZE = 13f

    private const val CHAR_WIDTH_PER_TEXT_SIZE = 0.58
    private const val LINE_HEIGHT_DP = 18.0
    /** MapKit переносит подписи длиннее ~20 символов на вторую строку. */
    private const val SINGLE_LINE_MAX_CHARS = 20
    private const val LABEL_SIDE_OFFSET_DP = 10.0
    private const val PADDING_DP = 6.0

    /** С какой стороны от точки рисовать подпись. */
    enum class Side { LEFT, RIGHT }

    /**
     * Подписи восточной половины маршрута — слева от точки, западной — справа:
     * так подписи конечных уходят внутрь карты, а не за край экрана.
     */
    fun labelSide(stop: Stop, routeStops: List<Stop>): Side {
        if (routeStops.isEmpty()) return Side.RIGHT
        val minLon = routeStops.minOf { it.location.longitude }
        val maxLon = routeStops.maxOf { it.location.longitude }
        return if (stop.location.longitude > (minLon + maxLon) / 2) Side.LEFT else Side.RIGHT
    }

    fun visibleLabelStopIds(
        stops: List<Stop>,
        zoom: Float,
        highlightedStopId: String? = null
    ): Set<String> {
        val unique = stops.distinctBy { it.id }
        if (unique.isEmpty()) return emptySet()

        val terminals = setOfNotNull(stops.firstOrNull()?.id, stops.lastOrNull()?.id)
        val prioritized = buildList {
            unique.firstOrNull { it.id == highlightedStopId }?.let(::add)
            unique.filter { it.id in terminals && it.id != highlightedStopId }.forEach(::add)
            if (zoom >= OVERVIEW_ZOOM) {
                unique.filter { it.id != highlightedStopId && it.id !in terminals }.forEach(::add)
            }
        }

        val accepted = LinkedHashSet<String>()
        val boxes = mutableListOf<Box>()
        for (stop in prioritized) {
            val box = labelBox(stop, zoom, highlighted = stop.id == highlightedStopId, side = labelSide(stop, unique))
            // Выделенную остановку подписываем всегда — пассажир только что её выбрал.
            if (stop.id == highlightedStopId || boxes.none { it.intersects(box) }) {
                accepted += stop.id
                boxes += box
            }
        }
        return accepted
    }

    /** Масштаб, сгруппированный по полшага: подписи не пересчитываются на каждом кадре жеста. */
    fun zoomBucket(zoom: Float): Int = (zoom * 2f).toInt()

    private fun labelBox(stop: Stop, zoom: Float, highlighted: Boolean, side: Side): Box {
        val worldSize = 256.0 * 2.0.pow(zoom.toDouble())
        val x = (stop.location.longitude + 180.0) / 360.0 * worldSize
        val latRad = stop.location.latitude * PI / 180.0
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * worldSize
        val size = if (highlighted) HIGHLIGHTED_TEXT_SIZE else LABEL_TEXT_SIZE
        // Подпись рисуется сбоку от точки (TextStyle.Placement.LEFT / RIGHT).
        val lines = if (stop.name.length > SINGLE_LINE_MAX_CHARS) 2 else 1
        val charsPerLine = if (lines == 2) stop.name.length / 2 + 3 else stop.name.length
        val width = charsPerLine * size * CHAR_WIDTH_PER_TEXT_SIZE + 2 * PADDING_DP
        val height = lines * LINE_HEIGHT_DP
        val left = when (side) {
            Side.RIGHT -> x + LABEL_SIDE_OFFSET_DP - PADDING_DP
            Side.LEFT -> x - LABEL_SIDE_OFFSET_DP + PADDING_DP - width
        }
        val top = y - height / 2 - PADDING_DP
        return Box(left, top, left + width, top + height + 2 * PADDING_DP)
    }

    private data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        fun intersects(other: Box): Boolean =
            left < other.right && other.left < right && top < other.bottom && other.top < bottom
    }
}
