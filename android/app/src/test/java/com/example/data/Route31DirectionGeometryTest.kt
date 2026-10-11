package com.example.data

import java.io.File
import java.security.MessageDigest
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
    private fun JsonObject.points(key: String): List<Pair<Double, Double>> =
        (getValue(key) as JsonArray).map {
            val o = it.jsonObject
            o.getValue("lat").jsonPrimitive.double to o.getValue("lon").jsonPrimitive.double
        }

    @Test
    fun all31EVariantsAreByteIdenticalToTheVerifiedSnapshot() {
        // SHA-256 сериализации каждого варианта 31Э в состоянии до правок 31
        // (коммиты 77557d3 … 1c964b9 — совпадают побайтно). 31Э менять нельзя.
        val expected = mapOf(
            "3467:0" to "3a77522448a84c31824e218b1f2f951048ef1e02513a47a02df08c9f2ef2b2ac",
            "3467:1" to "ef483d6223c6d372969a64bdd4dfde735f227df41d00e122de07f8ffe08fab41",
            "3467:2" to "88183ade3da4a8bd8384b4901f1a1699c4822079a161d7fd65db4b5ae051b32a",
            "3467:3" to "eb97975e8a4b2685dbe3ad0415370be88d0a3119c978d594c393851b12b2a7d4",
            "3467:4" to "2177f35ba25202eab8ac8a5d5c763f24e3933a3e64986d1c0f0332993165ec0f",
            "3467:5" to "3ee836e91601057df166fdeda4f94a3a72bd031501db6371e42e79f488a2fa51"
        )
        val actual = routes.filter { it.str("number") == "31Э" }.associate { r ->
            val serialized = r.toString()
            assertTrue("${r.str("id")} must appear verbatim in the asset", raw.contains(serialized))
            r.str("id") to sha256(serialized)
        }
        assertEquals(expected, actual)
    }

    @Test
    fun route31BIsNotShipped() {
        assertTrue(routes.none { it.str("number") == "31Б" || it.str("id").startsWith("400202:") })
    }

    @Test
    fun reverse31VariantsDoNotReturnTowardRmc2AfterTheAbkSpur() {
        // Единственный разворот в обратных вариантах — тупиковый заезд на АБК
        // Южный. Лишний возврат ~72 м к РМЦ-2 после заезда убран.
        listOf("2246:1", "2246:3", "2246:5").forEach { id ->
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

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
