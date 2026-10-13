package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.dto.ScheduleFileDto
import java.io.InputStream
import kotlinx.serialization.json.Json

/**
 * Загружает офлайн-расписание из assets/norilsk_schedule.json (официальные данные НПОПАТ).
 * Ключ — Route.id (направление-специфичный).
 *
 * Как и [RouteRepository], читается один раз и потокобезопасно; первый вызов
 * выполняется вне главного потока (MapViewModel, Dispatchers.IO).
 */
class ScheduleRepository(
    private val openAsset: (String) -> InputStream
) {
    constructor(context: Context) : this({ name -> context.applicationContext.assets.open(name) })

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val lock = Any()

    @Volatile
    private var loaded: Loaded? = null

    private class Loaded(val schedules: Map<String, RouteSchedule>, val dataDate: String)

    val dataDate: String
        get() = ensureLoaded().dataDate

    fun getScheduleMap(): Map<String, RouteSchedule> = ensureLoaded().schedules

    fun getSchedule(routeId: String): RouteSchedule? = getScheduleMap()[routeId]

    private fun ensureLoaded(): Loaded {
        loaded?.let { return it }
        return synchronized(lock) {
            loaded ?: load().also { loaded = it }
        }
    }

    private fun load(): Loaded = try {
        val text = openAsset(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val dto = json.decodeFromString<ScheduleFileDto>(text)
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
        Loaded(map, dto.dataDate)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load $ASSET_NAME", e)
        Loaded(emptyMap(), "")
    }

    private companion object {
        const val ASSET_NAME = "norilsk_schedule.json"
        const val TAG = "ScheduleRepository"
    }
}
