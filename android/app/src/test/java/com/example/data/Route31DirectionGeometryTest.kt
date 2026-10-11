package com.example.data

import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Геометрия обычного маршрута 31 после выверки заезда на АБК Южный и
 * неизменность 31Э. Работает прямо с ассетом, который уходит в APK.
 */
class Route31DirectionGeometryTest {
    private val raw = File("src/main/assets/norilsk_routes.json").readText()
    private val routes: List<JsonObject> =
        Json.parseToJsonElement(raw).jsonObject.getValue("routes").jsonArray.map { it.jsonObject }

    private fun route(id: String) = routes.first { it.str("id") == id }
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun stopNames(id: String) = route(id).getValue("stops").jsonArray.map { it.jsonObject.str("name") }
    private fun stopsOf(id: String) = route(id).getValue("stops").jsonArray.map { it.jsonObject }
    private fun JsonObject.at() = getValue("lat").jsonPrimitive.double to getValue("lon").jsonPrimitive.double
    private fun JsonObject.points(key: String): List<Pair<Double, Double>> =
        (getValue(key) as JsonArray).map {
            val o = it.jsonObject
            o.getValue("lat").jsonPrimitive.double to o.getValue("lon").jsonPrimitive.double
        }

    @Test
    fun all31EVariantsAreTheExpressNotACopyOf31() {
        // 2ГИС, Яндекс Карты и схема МУП 31e: на трассе Норильск – Кайеркан
        // экспресс останавливается только у Медного завода и Металлургического цеха.
        val express = listOf("Медный завод", "Металлургический цех")
        val kayerkan = listOf("Торгово-бытовой комплекс", "Строительная", "ТЦ (Кайеркан)", "Поликлиника (Кайеркан)", "Победы", "Торгово-бытовой комплекс")
        val expected = mapOf(
            "3467:0" to listOf("Административно-деловой центр") + express + kayerkan,
            "3467:1" to listOf("Торгово-бытовой комплекс") + express.reversed() + "Административно-деловой центр",
            "3467:2" to stopNames("2246:2").takeWhile { it != "Вокзальная" } + express + kayerkan,
            "3467:3" to listOf("Торгово-бытовой комплекс") + express.reversed() + stopNames("2246:3").dropWhile { it != "Административно-деловой центр" },
            "3467:4" to stopNames("2246:4").takeWhile { it != "Вокзальная" } + express + kayerkan,
            "3467:5" to listOf("Торгово-бытовой комплекс") + express.reversed() + stopNames("2246:5").dropWhile { it != "Красноярская" }
        )
        assertEquals(expected, routes.filter { it.str("number") == "31Э" }.associate { it.str("id") to stopNames(it.str("id")) })
        expected.keys.forEach { id ->
            val r = route(id)
            val twin = route(id.replace("3467:", "2246:"))
            assertEquals(id, listOf(twin.str("direction"), twin.str("origin"), twin.str("destination")),
                listOf(r.str("direction"), r.str("origin"), r.str("destination")))
        }
    }

    @Test
    fun express31ESkipsTheAbkSpurAndTheTsbkLoopAndNeverReverses() {
        val abk = 69.3215728 to 87.9627228
        val tsbk = 69.3282946 to 87.9787519
        routes.filter { it.str("number") == "31Э" }.forEach { r ->
            val id = r.str("id")
            val line = r.points("polyline")
            assertTrue("$id must not enter the ABK Yuzhny spur", line.minOf { meters(it, abk) } > 500.0)
            assertTrue("$id must not make the TsBK loop", line.minOf { meters(it, tsbk) } > 500.0)
            assertEquals("$id: express has no dead-end reversals", emptyList<Pair<Double, Double>>(), uTurns(line))
            assertFalse("$id: polyline self-intersects", selfIntersects(line))
        }
    }

    @Test
    fun route31BIsNotShipped() {
        assertTrue(routes.none { it.str("number") == "31Б" || it.str("id").startsWith("400202:") })
    }

    @Test
    fun every31VariantReversesOnlyAtTheAbkYuzhnyDeadEnd() {
        // Единственный разворот в любом варианте 31 — тупиковый заезд на АБК
        // Южный. Лишние «туда-обратно» к старой точке РМЦ-2 на трассе убраны.
        (0..5).map { "2246:$it" }.forEach { id ->
            val r = route(id)
            val turns = uTurns(r.points("polyline"))
            val abk = r.points("stops")[r.getValue("stops").jsonArray.indexOfFirst {
                it.jsonObject.str("name") == "АБК Южный"
            }]
            assertEquals("$id: only the ABK Yuzhny spur may reverse, got $turns", 1, turns.size)
            assertTrue("$id: the reversal must be at АБК Южный", meters(turns.single(), abk) < 5.0)
        }
    }

    @Test
    fun every31VariantServesAbkYuzhnyWithStopsOnTheLineInOrder() {
        routes.filter { it.str("number") == "31" }.forEach { r ->
            val id = r.str("id")
            val line = r.points("polyline")
            val stops = r.points("stops")
            assertTrue(id, r.getValue("stops").jsonArray.any { it.jsonObject.str("name") == "АБК Южный" })
            var lastIndex = 0
            stops.forEachIndexed { k, stop ->
                // ближайшая точка линии не раньше предыдущей остановки
                val (index, distance) = (lastIndex until line.size)
                    .map { it to meters(line[it], stop) }
                    .minBy { it.second }
                assertTrue("$id stop #$k is ${distance.toInt()} m from the line", distance <= 40.0)
                lastIndex = index
            }
            assertTrue("$id: a segment is implausibly long", line.zipWithNext().all { (a, b) -> meters(a, b) < 800.0 })
            assertFalse("$id: polyline self-intersects", selfIntersects(line))
        }
    }

    // Эталонные точки ниже — платформы и вершины дороги из 2ГИС (маршруты
    // 10696654705393696 / 10696654705393698), подтверждённые Яндекс Картами.

    @Test
    fun rmc2SitsOnTheAbkSpurNotOnTheTrunkRoad() {
        (0..5).map { "2246:$it" }.forEach { id ->
            val line = route(id).points("polyline")
            val rmc2 = stopsOf(id).single { it.str("name").startsWith("РМЦ-2") }.at()
            assertTrue("$id: РМЦ-2 must be at the 2GIS platform", meters(rmc2, 69.319531 to 87.968543) < 15.0)
            assertTrue("$id: РМЦ-2 must lie on the line", line.minOf { meters(it, rmc2) } < 2.0)
            // прямой отросток от развилки к АБК, без дуги ~80 м восточнее
            assertTrue("$id: spur must follow the straight road", line.minOf { meters(it, 69.32067 to 87.96566) } < 5.0)
            assertTrue("$id: the old bent spur is gone", line.all { meters(it, 69.32008 to 87.969166) > 30.0 })
        }
    }

    @Test
    fun adcDepartureGoesNorthRoundTheBlockAndArrivalsComeNorthbound() {
        val departure = 69.3597391 to 88.167439          // 22519, 2ГИС 805381
        listOf("2246:0", "2246:2", "2246:4", "3467:0", "3467:2", "3467:4").forEach { id ->
            val line = route(id).points("polyline")
            val i = line.indexOfFirst { meters(it, departure) < 1.0 }
            assertTrue("$id: line must pass the departure platform", i >= 0)
            assertTrue("$id: bus leaves the platform northbound", line[i + 1].first > line[i].first)
            if (i > 0) assertTrue("$id: bus arrives at the platform northbound", line[i - 1].first < line[i].first)
            // петля по часовой: верхний проезд квартала, затем вниз по западной улице
            assertTrue("$id: loop round the block", line.minOf { meters(it, 69.36082 to 88.16546) } < 5.0)
            assertTrue("$id: no cut across the block", line.all { meters(it, 69.36053 to 88.16876) > 30.0 })
        }
        listOf("2246:3", "3467:3").forEach { id ->
            val adc = stopsOf(id).single { it.str("name") == "Административно-деловой центр" }
            assertEquals("$id: through service calls at the street platform", "22332", adc.str("id"))
            val line = route(id).points("polyline")
            assertTrue("$id: no loop in the ADC yard", line.all { meters(it, 69.359247 to 88.168877) > 50.0 })
        }
    }

    @Test
    fun khlebozavodTerminalsUseTheCityWideStops() {
        mapOf("2246:2" to "22344", "2246:4" to "22344", "3467:2" to "22344", "3467:4" to "22344").forEach { (id, sid) ->
            assertEquals(id, sid, stopsOf(id).first().str("id"))
            assertTrue(id, meters(route(id).points("polyline").first(), 69.338485 to 88.225091) < 1.0)
        }
        mapOf("2246:3" to "22343", "2246:5" to "22343", "3467:3" to "22343", "3467:5" to "22343").forEach { (id, sid) ->
            assertEquals(id, sid, stopsOf(id).last().str("id"))
            assertTrue(id, meters(route(id).points("polyline").last(), 69.337615 to 88.224548) < 1.0)
        }
    }

    @Test
    fun regular31CallsAtVzhkAndExpressDoesNot() {
        (0..5).forEach { k ->
            val names = stopNames("2246:$k")
            val tbk = if (k % 2 == 0) names.indexOfFirst { it == "Торгово-бытовой комплекс" } else 0
            val vzhk = names.indexOf("ВЖК")
            assertEquals("2246:$k: ВЖК sits next to ТБК", if (k % 2 == 0) tbk - 1 else 1, vzhk)
            assertFalse("3467:$k", "ВЖК" in stopNames("3467:$k"))
        }
    }

    @Test
    fun route31FamilyHasNoRepeatedPointsAndSharedStopsAgreeEverywhere() {
        val family = routes.filter { it.str("number") == "31" || it.str("number") == "31Э" }
        family.forEach { r ->
            assertTrue(r.str("id"), r.points("polyline").zipWithNext().none { (a, b) -> a == b })
        }
        val familyIds = family.flatMap { r -> stopsOf(r.str("id")).map { it.str("id") } }.toSet()
        routes.flatMap { it.getValue("stops").jsonArray.map { s -> s.jsonObject } }
            .filter { it.str("id") in familyIds }
            .groupBy { it.str("id") }
            .forEach { (sid, copies) ->
                val first = copies.first().at()
                assertTrue("stop $sid has different coordinates in different routes", copies.all { meters(it.at(), first) < 1.0 })
            }
    }

    private fun uTurns(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val p = points.fold(mutableListOf<Pair<Double, Double>>()) { acc, pt ->
            if (acc.isEmpty() || meters(acc.last(), pt) >= 0.5) acc.add(pt); acc
        }
        return (1 until p.size - 1).filter { i ->
            val a = xy(p[i - 1]); val b = xy(p[i]); val c = xy(p[i + 1])
            val v1 = (b.first - a.first) to (b.second - a.second)
            val v2 = (c.first - b.first) to (c.second - b.second)
            val dot = v1.first * v2.first + v1.second * v2.second
            dot / (hypot(v1.first, v1.second) * hypot(v2.first, v2.second)) < -0.94
        }.map { p[it] }
    }

    private fun selfIntersects(line: List<Pair<Double, Double>>): Boolean {
        val pts = line.map(::xy)
        fun orient(p: Pair<Double, Double>, q: Pair<Double, Double>, r: Pair<Double, Double>): Int {
            val v = (q.first - p.first) * (r.second - p.second) - (q.second - p.second) * (r.first - p.first)
            return if (kotlin.math.abs(v) < 1e-9) 0 else if (v > 0) 1 else -1
        }
        for (i in 0 until pts.size - 1) for (j in i + 2 until pts.size - 1) {
            if (orient(pts[i], pts[i + 1], pts[j]) * orient(pts[i], pts[i + 1], pts[j + 1]) < 0 &&
                orient(pts[j], pts[j + 1], pts[i]) * orient(pts[j], pts[j + 1], pts[i + 1]) < 0
            ) return true
        }
        return false
    }

    private fun xy(p: Pair<Double, Double>) = p.second * 111_320.0 * cos(Math.toRadians(69.35)) to p.first * 110_540.0
    private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val pa = xy(a); val pb = xy(b)
        return hypot(pa.first - pb.first, pa.second - pb.second)
    }

}
