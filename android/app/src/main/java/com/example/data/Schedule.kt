package com.example.data

/** Доменная модель расписания одного маршрута (направления). Времена в формате "HH:MM". */
data class RouteSchedule(
    val routeId: String,
    val number: String,
    val hasSchedule: Boolean,
    val terminal: String?,          // конечная этого направления (для «через X мин»)
    val weekday: List<String>,      // отправления с конечной — будни
    val weekend: List<String>,      // отправления с конечной — выходные
    val timetable: List<TerminalTimetable> // полное расписание по всем конечным
)

data class TerminalTimetable(
    val terminal: String,
    val weekday: List<String>,
    val weekend: List<String>
)
