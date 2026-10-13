package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteColorsTest {
    private val yellow31 = 4294961979L // colorArgb маршрутов 31/31Э из ассета
    private val white = 0xFFFFFFFFL

    @Test
    fun yellowBadgeGetsDarkDigits() {
        assertEquals(RouteColors.DARK_TEXT, RouteColors.badgeTextColor(yellow31))
        assertTrue(RouteColors.contrastRatio(RouteColors.badgeTextColor(yellow31), yellow31) >= 4.5)
    }

    @Test
    fun darkBadgeKeepsWhiteDigits() {
        val darkBlue = 0xFF0D47A1L
        assertEquals(RouteColors.LIGHT_TEXT, RouteColors.badgeTextColor(darkBlue))
    }

    @Test
    fun yellowChipTextIsDarkenedToReadableContrast() {
        val container = RouteColors.composite(yellow31, 0.14, white)
        val label = RouteColors.readableOn(yellow31, container)
        assertTrue(RouteColors.contrastRatio(label, container) >= RouteColors.MIN_TEXT_CONTRAST)
        assertTrue("original yellow is not readable", RouteColors.contrastRatio(yellow31, container) < 2.0)
    }

    @Test
    fun alreadyReadableColorIsKept() {
        val darkGreen = 0xFF1B5E20L
        assertEquals(darkGreen, RouteColors.readableOn(darkGreen, white))
    }
}
