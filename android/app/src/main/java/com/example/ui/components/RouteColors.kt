package com.example.ui.components

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Читаемые цвета для номеров маршрутов.
 *
 * Цвета маршрутов берутся из данных перевозчика, среди них есть светло-жёлтые
 * (31, 31Э, 15). Белый номер на таком бейдже и жёлтый текст на светлом чипе
 * почти не видны, поэтому цвет текста подбирается по контрасту WCAG.
 * Функции работают с ARGB-значениями, чтобы их можно было проверять обычными
 * unit-тестами без Compose.
 */
object RouteColors {
    const val DARK_TEXT: Long = 0xFF111111
    const val LIGHT_TEXT: Long = 0xFFFFFFFF

    /** Минимальный контраст для обычного текста (WCAG AA). */
    const val MIN_TEXT_CONTRAST = 4.5

    /** Цвет номера на сплошном бейдже цвета маршрута: тёмный или белый, что контрастнее. */
    fun badgeTextColor(routeArgb: Long): Long =
        if (contrastRatio(DARK_TEXT, routeArgb) >= contrastRatio(LIGHT_TEXT, routeArgb)) DARK_TEXT else LIGHT_TEXT

    /** Тень под белым номером нужна только на тёмных бейджах; на светлых она даёт «грязь». */
    fun badgeNeedsShadow(routeArgb: Long): Boolean = badgeTextColor(routeArgb) == LIGHT_TEXT

    /**
     * Цвет маршрута, затемнённый (на светлом фоне) или осветлённый (на тёмном)
     * ровно настолько, чтобы текст на [backgroundArgb] читался.
     */
    fun readableOn(routeArgb: Long, backgroundArgb: Long, minRatio: Double = MIN_TEXT_CONTRAST): Long {
        if (contrastRatio(routeArgb, backgroundArgb) >= minRatio) return opaque(routeArgb)
        val towards = if (luminance(backgroundArgb) > 0.5) 0xFF000000 else 0xFFFFFFFF
        var step = 0.05
        while (step <= 1.0) {
            val candidate = mix(routeArgb, towards, step)
            if (contrastRatio(candidate, backgroundArgb) >= minRatio) return candidate
            step += 0.05
        }
        return opaque(towards)
    }

    /** Цвет [overlayArgb] с прозрачностью [alpha], наложенный на непрозрачный [baseArgb]. */
    fun composite(overlayArgb: Long, alpha: Double, baseArgb: Long): Long = mix(baseArgb, overlayArgb, alpha)

    fun contrastRatio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun mix(from: Long, to: Long, t: Double): Long {
        fun ch(shift: Int): Long {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * t).coerceIn(0, 255)
        }
        return 0xFF000000 or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun opaque(argb: Long): Long = 0xFF000000 or (argb and 0xFFFFFF)
}
