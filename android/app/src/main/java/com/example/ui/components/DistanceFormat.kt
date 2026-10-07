package com.example.ui.components

import kotlin.math.roundToInt

/**
 * Человеческое представление расстояния.
 *
 * До километра считаем метрами и округляем до десятков: пассажиру не нужна
 * точность до метра, а «137 м» создаёт ложное ощущение точности GPS.
 * Дальше — километры.
 */
fun formatDistance(meters: Double): String {
    if (!meters.isFinite() || meters < 0) return "—"
    return when {
        meters < 100 -> "${(meters / 10).roundToInt() * 10} м"
        meters < 1000 -> "${(meters / 50).roundToInt() * 50} м"
        meters < 10_000 -> {
            val km = meters / 1000.0
            "${String.format("%.1f", km).replace('.', ',')} км"
        }
        else -> "${(meters / 1000).roundToInt()} км"
    }
}

/** Расстояние, за которым остановки уже нельзя считать «ближайшими». */
const val NEARBY_STOPS_LIMIT_METERS = 5_000.0
