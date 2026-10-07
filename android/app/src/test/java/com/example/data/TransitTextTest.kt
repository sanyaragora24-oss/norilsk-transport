package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitTextTest {

    @Test
    fun routeNumbersUseNumericNaturalOrder() {
        val actual = listOf("31Э", "11", "5Б", "2", "31", "1Б", "40К", "5А", "1А", "31Б")
            .sortedWith(Comparator(::compareRouteNumbers))

        assertEquals(
            listOf("1А", "1Б", "2", "5А", "5Б", "11", "31", "31Б", "31Э", "40К"),
            actual
        )
    }

    @Test
    fun routeSuffixCanBeEnteredWithLatinKeyboard() {
        assertTrue(matchesTransitSearch("31e", "31Э", "АДЦ", "ТБК"))
        assertTrue(matchesTransitSearch("5a", "5А", "ОВЦ", "Медный завод"))
    }

    @Test
    fun searchIgnoresCaseNumberSignPunctuationAndYo() {
        assertEquals("маршрут 31э хлебозавод", normalizeTransitSearch("  МАРШРУТ №31Э — Хлебозавод "))
        assertTrue(matchesTransitSearch("№31 хлебо", "31", "Хлебозавод", "ТБК"))
        assertTrue(matchesTransitSearch("федоровского", "Фёдоровского, 19"))
        assertFalse(matchesTransitSearch("талнахская", "31", "АДЦ", "ТБК"))
    }
}
