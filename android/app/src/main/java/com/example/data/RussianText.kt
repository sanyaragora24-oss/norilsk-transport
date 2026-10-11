package com.example.data

import kotlin.math.abs

/**
 * Русские числительные: 1 пересадка, 2 пересадки, 5 пересадок.
 * Названия остановок в родительный падеж не склоняем (данные перевозчика,
 * ошибки неизбежны), поэтому формулировки строятся вокруг именительного:
 * «→ Торгово-бытовой комплекс», «Отправление: Административно-деловой центр».
 */
fun pluralRu(count: Int, one: String, few: String, many: String): String {
    val n = abs(count) % 100
    val last = n % 10
    return when {
        n in 11..14 -> many
        last == 1 -> one
        last in 2..4 -> few
        else -> many
    }
}

fun transfersLabel(count: Int): String = pluralRu(count, "Пересадка", "Пересадки", "Пересадок")

fun transfersPhrase(count: Int): String =
    if (count == 0) "без пересадок" else "$count ${pluralRu(count, "пересадка", "пересадки", "пересадок")}"

/** Источник оценки «через N мин» на карточке остановки. */
enum class ArrivalSource { LIVE_GPS, SCHEDULE }

fun arrivalPhrase(minutes: Int): String = if (minutes <= 0) "сейчас" else "через ~$minutes мин"

fun arrivalSourceLabel(source: ArrivalSource): String = when (source) {
    ArrivalSource.LIVE_GPS -> "по GPS"
    ArrivalSource.SCHEDULE -> "по расписанию"
}
