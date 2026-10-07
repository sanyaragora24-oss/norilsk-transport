package com.example.data

import android.location.Location
import com.yandex.mapkit.geometry.Point
import java.util.Calendar

/**
 * Оценивает «через сколько минут» придёт ближайший рейс на остановку — ПО РАСПИСАНИЮ (офлайн),
 * когда нет живых данных GPS. Логика: берём отправления с конечной этого направления и
 * прибавляем примерное время хода до целевой остановки (по геометрии маршрута).
 * Это ОРИЕНТИРОВОЧНО (без учёта пробок).
 */
object ScheduleEstimator {

    // Средняя городская скорость автобуса, м/с (~18 км/ч) — как в ArrivalEstimator.
    private const val AVG_SPEED_MPS = 5.0

    fun isWeekend(cal: Calendar = Calendar.getInstance()): Boolean {
        val d = cal.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    fun nowMinutes(cal: Calendar = Calendar.getInstance()): Int =
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    private fun parseMinutes(t: String): Int? {
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** Примерное время хода (мин) от первой остановки маршрута до целевой. */
    private fun offsetMinutes(route: Route, stopId: String): Int {
        val idx = route.stops.indexOfFirst { it.id == stopId }
        if (idx <= 0) return 0
        var dist = 0.0
        for (i in 0 until idx) {
            dist += distanceMeters(route.stops[i].location, route.stops[i + 1].location)
        }
        return Math.round(dist / AVG_SPEED_MPS / 60.0).toInt()
    }

    /** @return минут до ближайшего рейса по расписанию, либо null если рейсов больше нет/нет данных. */
    fun minutesUntilNext(
        route: Route,
        schedule: RouteSchedule?,
        stopId: String,
        nowMin: Int = nowMinutes(),
        weekend: Boolean = isWeekend()
    ): Int? {
        if (schedule == null || !schedule.hasSchedule || schedule.terminal == null) return null
        val deps = (if (weekend) schedule.weekend else schedule.weekday).mapNotNull { parseMinutes(it) }
        if (deps.isEmpty()) return null
        val offset = offsetMinutes(route, stopId)
        var best: Int? = null
        for (d in deps) {
            val arrival = d + offset
            if (arrival >= nowMin) {
                val diff = arrival - nowMin
                val current = best
                if (current == null || diff < current) best = diff
            }
        }
        return best
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val res = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, res)
        return res[0].toDouble()
    }
}
