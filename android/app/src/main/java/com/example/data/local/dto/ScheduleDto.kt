package com.example.data.local.dto

import kotlinx.serialization.Serializable

/**
 * DTO расписания из assets/norilsk_schedule.json.
 * Ключ верхней карты routes — это id маршрута (направление-специфичный),
 * совпадает с Route.id из norilsk_routes.json.
 */
@Serializable
data class ScheduleFileDto(
    val dataDate: String = "",
    val routes: Map<String, RouteScheduleDto> = emptyMap()
)

@Serializable
data class RouteScheduleDto(
    val number: String = "",
    val hasSchedule: Boolean = false,
    // конечная остановка для ЭТОГО направления (для расчёта «через X мин»); может быть null
    val terminal: String? = null,
    val weekday: List<String> = emptyList(),
    val weekend: List<String> = emptyList(),
    // полное расписание по всем конечным (для отображения)
    val timetable: List<TerminalTimetableDto> = emptyList()
)

@Serializable
data class TerminalTimetableDto(
    val terminal: String = "",
    val weekday: List<String> = emptyList(),
    val weekend: List<String> = emptyList()
)
