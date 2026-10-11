package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RussianTextTest {
    @Test
    fun transferLabelAgreesWithCount() {
        assertEquals("Пересадок", transfersLabel(0))
        assertEquals("Пересадка", transfersLabel(1))
        assertEquals("Пересадки", transfersLabel(2))
        assertEquals("Пересадки", transfersLabel(4))
        assertEquals("Пересадок", transfersLabel(5))
        assertEquals("Пересадок", transfersLabel(11))
        assertEquals("Пересадка", transfersLabel(21))
    }

    @Test
    fun transferPhraseForPlannerChips() {
        assertEquals("без пересадок", transfersPhrase(0))
        assertEquals("1 пересадка", transfersPhrase(1))
        assertEquals("3 пересадки", transfersPhrase(3))
    }

    @Test
    fun arrivalPhraseExplainsMinutes() {
        assertEquals("через ~89 мин", arrivalPhrase(89))
        assertEquals("сейчас", arrivalPhrase(0))
        assertEquals("по расписанию", arrivalSourceLabel(ArrivalSource.SCHEDULE))
        assertEquals("по GPS", arrivalSourceLabel(ArrivalSource.LIVE_GPS))
    }

    @Test
    fun hyphenatedNamesGetAnInvisibleBreakOpportunity() {
        assertEquals("Административно-\u200Bделовой центр", breakableAfterHyphens("Административно-деловой центр"))
        assertEquals("Пождепо (Надежда)", breakableAfterHyphens("Пождепо (Надежда)"))
    }
}
