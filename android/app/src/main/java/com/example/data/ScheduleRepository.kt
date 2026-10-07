package com.example.data

import android.content.Context
import com.example.data.local.dto.ScheduleFileDto
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

/**
 * Загружает офлайн-расписание из assets/norilsk_schedule.json (официальные данные НПОПАТ).
 * Ключ — Route.id (направление-специфичный).
 */
class ScheduleRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var cached: Map<String, RouteSchedule>? = null
    var dataDate: String = ""
        private set

    fun getScheduleMap(): Map<String, RouteSchedule> {
        cached?.let { return it }
        return try {
            val text = InputStreamReader(context.assets.open("norilsk_schedule.json")).readText()
            val dto = json.decodeFromString<ScheduleFileDto>(text)
            dataDate = dto.dataDate
            val map = dto.routes.mapValues { (rid, r) ->
                RouteSchedule(
                    routeId = rid,
                    number = r.number,
                    hasSchedule = r.hasSchedule,
                    terminal = r.terminal,
                    weekday = r.weekday,
                    weekend = r.weekend,
                    timetable = r.timetable.map {
                        TerminalTimetable(it.terminal, it.weekday, it.weekend)
                    }
                )
            }
            cached = map
            map
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    fun getSchedule(routeId: String): RouteSchedule? = getScheduleMap()[routeId]
}
