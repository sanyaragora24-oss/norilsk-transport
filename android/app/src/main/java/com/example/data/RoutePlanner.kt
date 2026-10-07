package com.example.data

import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Офлайн-планировщик поездок А→Б с пересадками поверх статической базы маршрутов
 * (assets/norilsk_routes.json). Без интернета: считает по геометрии остановок.
 *
 * Модель графа:
 *  - остановки кластеризуются по названию (обе стороны одной остановки = один узел);
 *  - состояние Дейкстры = (кластер, маршрут|пешком);
 *  - переходы: посадка (ожидание), проезд по маршруту, высадка, пеший переход
 *    между близкими остановками (≤ WALK_RADIUS).
 */
object RoutePlanner {

    private const val BUS_SPEED_MPS = 6.5      // ~23 км/ч в городе
    private const val WALK_SPEED_MPS = 1.25    // ~4.5 км/ч
    private const val WAIT_SECONDS = 5.0 * 60  // среднее ожидание/посадка
    private const val WALK_RADIUS_M = 350.0    // макс. длина одного пешего перехода
    private const val MERGE_RADIUS_M = 600.0   // остановки с одинаковым именем сливаем в кластер только если ближе этого
    private const val WALK = "WALK"

    enum class StepType { WALK, RIDE }

    data class JourneyStep(
        val type: StepType,
        val routeNumber: String? = null,
        val routeId: String? = null,
        val routeColor: Long = 0L,
        val fromStopName: String,
        val toStopName: String,
        val rideStopsCount: Int = 0,
        val distanceMeters: Double,
        val durationMinutes: Int
    )

    data class Journey(
        val steps: List<JourneyStep>,
        val totalMinutes: Int,
        val totalDistanceMeters: Double,
        val walkMeters: Double,
        val transfers: Int
    )

    // --- внутренние структуры графа ---
    private data class Cluster(val idx: Int, val name: String, var lat: Double, var lon: Double, val stopIds: MutableSet<String>)
    private data class RoutePos(val routeIdx: Int, val stopIndex: Int)
    private data class Edge(val type: String, val routeIdx: Int, val toState: String, val seconds: Double, val meters: Double, val toCluster: Int)

    private class Graph(
        val clusters: List<Cluster>,
        val clusterOfStop: Map<String, Int>,
        val routesThroughCluster: Map<Int, List<RoutePos>>,
        val nearby: Map<Int, List<Pair<Int, Double>>>,
        val routes: List<Route>
    )

    private fun normalize(name: String): String =
        name.lowercase().replace(Regex("[^a-zа-яё0-9]+"), " ").trim()

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun buildGraph(routes: List<Route>): Graph {
        // имя -> список кластеров с таким именем (их может быть несколько в разных частях города,
        // напр. «Гараж» в Норильске и «Гараж» в Талнахе — их НЕЛЬЗЯ сливать)
        val nameToClusters = HashMap<String, MutableList<Int>>()
        val clusters = ArrayList<Cluster>()
        val clusterOfStop = HashMap<String, Int>()
        val accum = HashMap<Int, MutableList<Pair<Double, Double>>>()

        fun clusterFor(stop: Stop): Int {
            val key = normalize(stop.name)
            val lat = stop.location.latitude
            val lon = stop.location.longitude
            val candidates = nameToClusters.getOrPut(key) { mutableListOf() }
            // выбираем ближайший кластер с тем же именем в пределах MERGE_RADIUS_M
            var chosen = -1
            var best = Double.MAX_VALUE
            for (ci in candidates) {
                val pts = accum[ci]!!
                val cLat = pts.sumOf { it.first } / pts.size
                val cLon = pts.sumOf { it.second } / pts.size
                val d = haversine(lat, lon, cLat, cLon)
                if (d <= MERGE_RADIUS_M && d < best) { best = d; chosen = ci }
            }
            if (chosen < 0) {
                val c = Cluster(clusters.size, stop.name, 0.0, 0.0, mutableSetOf())
                clusters.add(c)
                accum[c.idx] = mutableListOf()
                candidates.add(c.idx)
                chosen = c.idx
            }
            clusters[chosen].stopIds.add(stop.id)
            clusterOfStop[stop.id] = chosen
            accum[chosen]!!.add(lat to lon)
            return chosen
        }

        routes.forEach { r -> r.stops.forEach { clusterFor(it) } }
        // центроиды
        accum.forEach { (idx, pts) ->
            clusters[idx].lat = pts.sumOf { it.first } / pts.size
            clusters[idx].lon = pts.sumOf { it.second } / pts.size
        }

        // позиции маршрутов в кластерах
        val routesThrough = HashMap<Int, MutableList<RoutePos>>()
        routes.forEachIndexed { ri, route ->
            route.stops.forEachIndexed { si, stop ->
                val c = clusterOfStop[stop.id] ?: return@forEachIndexed
                routesThrough.getOrPut(c) { mutableListOf() }.add(RoutePos(ri, si))
            }
        }

        // соседи для пеших переходов (грид по 0.003° ≈ 330 м)
        val grid = HashMap<Long, MutableList<Int>>()
        fun cell(lat: Double, lon: Double): Long {
            val gx = (lat / 0.003).toLong()
            val gy = (lon / 0.008).toLong()
            return gx * 100000L + gy
        }
        clusters.forEach { grid.getOrPut(cell(it.lat, it.lon)) { mutableListOf() }.add(it.idx) }
        val nearby = HashMap<Int, MutableList<Pair<Int, Double>>>()
        clusters.forEach { c ->
            val gx = (c.lat / 0.003).toLong()
            val gy = (c.lon / 0.008).toLong()
            val list = nearby.getOrPut(c.idx) { mutableListOf() }
            for (dx in -1..1) for (dy in -1..1) {
                grid[(gx + dx) * 100000L + (gy + dy)]?.forEach { other ->
                    if (other != c.idx) {
                        val d = haversine(c.lat, c.lon, clusters[other].lat, clusters[other].lon)
                        if (d <= WALK_RADIUS_M) list.add(other to d)
                    }
                }
            }
        }

        return Graph(clusters, clusterOfStop, routesThrough, nearby, routes)
    }

    private fun stateKey(cluster: Int, route: String) = "$cluster|$route"

    /** Строит лучший по времени маршрут от [from] до [to]. */
    fun plan(routes: List<Route>, from: Stop, to: Stop): Journey? =
        planVariants(routes, from, to).firstOrNull()

    fun planVariantsFromLocation(
        routes: List<Route>, origin: com.yandex.mapkit.geometry.Point, to: Stop
    ): List<Journey> {
        if (routes.isEmpty() || !origin.latitude.isFinite() || !origin.longitude.isFinite() ||
            origin.latitude !in -90.0..90.0 || origin.longitude !in -180.0..180.0
        ) return emptyList()
        val graph = buildGraph(routes)
        // The closest stop can serve only the opposite direction. Consider nearby
        // boarding clusters, with an explicit walking leg rather than teleporting.
        val candidates = routes.asSequence().flatMap { it.stops.asSequence() }
            .distinctBy { it.id }
            .map { stop -> stop to haversine(origin.latitude, origin.longitude, stop.location.latitude, stop.location.longitude) }
            .filter { (_, distance) -> distance <= 750.0 }
            .sortedBy { it.second }
            .distinctBy { graph.clusterOfStop[it.first.id] }
            .take(8)
            .toList()
        return candidates.flatMap { (stop, distance) ->
            planVariants(graph, stop, to).map { journey ->
                if (distance < 1.0) journey else {
                    val walkMinutes = kotlin.math.ceil(distance / WALK_SPEED_MPS / 60.0).toInt()
                    val walk = JourneyStep(StepType.WALK, fromStopName = "Моё местоположение",
                        toStopName = stop.name, distanceMeters = distance, durationMinutes = walkMinutes)
                    journey.copy(steps = listOf(walk) + journey.steps,
                        totalMinutes = journey.totalMinutes + walkMinutes,
                        totalDistanceMeters = journey.totalDistanceMeters + distance,
                        walkMeters = journey.walkMeters + distance)
                }
            }
        }.sortedWith(compareBy({ it.transfers }, { it.totalMinutes }))
            .distinctBy { journey -> journey.steps.filter { it.type == StepType.RIDE }
                .joinToString(">") { it.routeNumber ?: "" } }
            .take(5)
    }

    /**
     * Возвращает до 3 РАЗНЫХ вариантов поездки А→Б:
     *  - прямые маршруты без пересадок (если есть),
     *  - быстрый по времени,
     *  - вариант с минимумом пересадок.
     * Отсортированы: сначала меньше пересадок, потом быстрее. Дубли по набору маршрутов убраны.
     */
    fun planVariants(routes: List<Route>, from: Stop, to: Stop): List<Journey> {
        if (routes.isEmpty()) return emptyList()
        return planVariants(buildGraph(routes), from, to)
    }

    private fun planVariants(g: Graph, from: Stop, to: Stop): List<Journey> {
        val startC = g.clusterOfStop[from.id] ?: return emptyList()
        val goalC = g.clusterOfStop[to.id] ?: return emptyList()
        if (startC == goalC) return emptyList()

        // 1) ВСЕ прямые маршруты без пересадок — показываем максимум вариантов «сел и доехал»
        val directs = directJourneys(g, startC, goalC).sortedBy { it.totalMinutes }

        // 2) Варианты С ПЕРЕСАДКАМИ. Делаем несколько РАЗНЫХ: после первого пути
        //    «баним» использованные маршруты и ищем альтернативу — чтобы не дублировать.
        val transferJourneys = ArrayList<Journey>()
        val banned = HashSet<Int>()
        dijkstra(g, startC, goalC, WAIT_SECONDS, banned)?.let {
            if (it.transfers > 0) { transferJourneys.add(it); banned.addAll(rideRouteIdxs(g, it)) }
        }
        dijkstra(g, startC, goalC, WAIT_SECONDS, banned)?.let {
            if (it.transfers > 0) { transferJourneys.add(it); banned.addAll(rideRouteIdxs(g, it)) }
        }
        // вариант с минимумом пересадок (большой штраф за каждую посадку)
        dijkstra(g, startC, goalC, 25.0 * 60)?.let {
            if (it.transfers > 0) transferJourneys.add(it)
        }

        // Склейка: сначала ВСЕ прямые (до 4), затем пересадочные (до 3) —
        // так гарантированно показываем И прямые, И с пересадками, когда они есть.
        val out = ArrayList<Journey>()
        out.addAll(directs.take(4))
        out.addAll(transferJourneys.sortedWith(compareBy({ it.transfers }, { it.totalMinutes })).take(3))

        // дедуп по сигнатуре маршрутов
        val seen = HashSet<String>()
        val uniq = out.filter { j ->
            val sig = j.steps.filter { it.type == StepType.RIDE }.joinToString(">") { it.routeNumber ?: "" }
            sig.isNotEmpty() && seen.add(sig)
        }
        // итог: меньше пересадок → быстрее; всего до 5 вариантов
        return uniq.sortedWith(compareBy({ it.transfers }, { it.totalMinutes })).take(5)
    }

    /** Набор индексов маршрутов, задействованных в поездке (по RIDE-шагам). */
    private fun rideRouteIdxs(g: Graph, j: Journey): Set<Int> =
        j.steps.filter { it.type == StepType.RIDE }
            .mapNotNull { st -> g.routes.indexOfFirst { it.id == st.routeId }.takeIf { idx -> idx >= 0 } }
            .toSet()

    /** Все варианты «доехать на одном маршруте» (от before to в одном направлении). */
    private fun directJourneys(g: Graph, startC: Int, goalC: Int): List<Journey> {
        val result = ArrayList<Journey>()
        g.routes.forEachIndexed { ri, route ->
            val fromIdx = route.stops.indexOfFirst { g.clusterOfStop[it.id] == startC }
            val toIdx = route.stops.indexOfLast { g.clusterOfStop[it.id] == goalC }
            if (fromIdx in 0 until toIdx) {
                var meters = 0.0
                for (k in fromIdx until toIdx) {
                    val a = route.stops[k]; val b = route.stops[k + 1]
                    meters += haversine(a.location.latitude, a.location.longitude, b.location.latitude, b.location.longitude)
                }
                val sec = WAIT_SECONDS + meters / BUS_SPEED_MPS
                result.add(
                    Journey(
                        steps = listOf(
                            JourneyStep(
                                type = StepType.RIDE,
                                routeNumber = route.number,
                                routeId = route.id,
                                routeColor = route.color,
                                fromStopName = g.clusters[startC].name,
                                toStopName = g.clusters[goalC].name,
                                rideStopsCount = toIdx - fromIdx,
                                distanceMeters = meters,
                                durationMinutes = Math.max(1, (meters / BUS_SPEED_MPS / 60).roundToInt())
                            )
                        ),
                        totalMinutes = Math.max(1, (sec / 60).roundToInt()),
                        totalDistanceMeters = meters,
                        walkMeters = 0.0,
                        transfers = 0
                    )
                )
            }
        }
        return result
    }

    private fun dijkstra(g: Graph, startC: Int, goalC: Int, waitSeconds: Double, bannedRoutes: Set<Int> = emptySet()): Journey? {

        val startState = stateKey(startC, WALK)
        val dist = HashMap<String, Double>()
        val prev = HashMap<String, Pair<String, Edge>>()
        dist[startState] = 0.0
        val pq = PriorityQueue<Pair<Double, String>>(compareBy { it.first })
        pq.add(0.0 to startState)

        var goalState: String? = null
        val goalKey = stateKey(goalC, WALK)

        while (pq.isNotEmpty()) {
            val item = pq.poll() ?: break
            val (d, state) = item
            if (d > (dist[state] ?: Double.MAX_VALUE)) continue
            if (state == goalKey) { goalState = state; break }

            val sep = state.indexOf('|')
            val cluster = state.substring(0, sep).toInt()
            val route = state.substring(sep + 1)

            val edges = ArrayList<Edge>()
            if (route == WALK) {
                // посадка на любой маршрут через кластер (кроме «забаненных» — для альтернативных вариантов)
                g.routesThroughCluster[cluster]?.map { it.routeIdx }?.distinct()?.forEach { ri ->
                    if (ri in bannedRoutes) return@forEach
                    val rid = g.routes[ri].id
                    edges.add(Edge("BOARD", ri, stateKey(cluster, rid), waitSeconds, 0.0, cluster))
                }
                // пеший переход к соседним остановкам
                g.nearby[cluster]?.forEach { (other, meters) ->
                    edges.add(Edge("WALK", -1, stateKey(other, WALK), meters / WALK_SPEED_MPS, meters, other))
                }
            } else {
                val ri = g.routes.indexOfFirst { it.id == route }
                // проезд к следующей остановке этого маршрута
                g.routesThroughCluster[cluster]?.filter { it.routeIdx == ri }?.forEach { pos ->
                    val rStops = g.routes[ri].stops
                    if (pos.stopIndex + 1 < rStops.size) {
                        val a = rStops[pos.stopIndex]
                        val b = rStops[pos.stopIndex + 1]
                        val toC = g.clusterOfStop[b.id] ?: return@forEach
                        if (toC == cluster) return@forEach
                        val meters = haversine(a.location.latitude, a.location.longitude, b.location.latitude, b.location.longitude)
                        edges.add(Edge("RIDE", ri, stateKey(toC, route), meters / BUS_SPEED_MPS, meters, toC))
                    }
                }
                // высадка (бесплатно) — позволяет пересесть или дойти пешком
                edges.add(Edge("ALIGHT", -1, stateKey(cluster, WALK), 0.0, 0.0, cluster))
            }

            edges.forEach { e ->
                val nd = d + e.seconds
                if (nd < (dist[e.toState] ?: Double.MAX_VALUE)) {
                    dist[e.toState] = nd
                    prev[e.toState] = state to e
                    pq.add(nd to e.toState)
                }
            }
        }

        if (goalState == null) return null

        // восстановление пути
        val edgesPath = ArrayList<Pair<Edge, Int>>() // edge + cluster прибытия
        var cur = goalState
        while (cur != startState) {
            val (p, e) = prev[cur] ?: break
            edgesPath.add(e to clusterIdxOf(cur!!))
            cur = p
        }
        edgesPath.reverse()

        return assemble(g, edgesPath, startC)
    }

    private fun clusterIdxOf(state: String): Int = state.substring(0, state.indexOf('|')).toInt()

    private fun assemble(g: Graph, path: List<Pair<Edge, Int>>, startCluster: Int): Journey? {
        val steps = ArrayList<JourneyStep>()
        var totalSec = 0.0
        var totalMeters = 0.0
        var walkMeters = 0.0
        var boards = 0

        var curCluster = startCluster
        // объединяем подряд идущие RIDE одного маршрута и WALK
        var i = 0
        while (i < path.size) {
            val (e, toC) = path[i]
            when (e.type) {
                "BOARD" -> { totalSec += e.seconds; boards++; i++ }
                "ALIGHT" -> { i++ }
                "WALK" -> {
                    // слить цепочку пеших переходов
                    var meters = 0.0; var sec = 0.0; var endC = curCluster
                    while (i < path.size && path[i].first.type == "WALK") {
                        meters += path[i].first.meters; sec += path[i].first.seconds; endC = path[i].second; i++
                    }
                    totalSec += sec; totalMeters += meters; walkMeters += meters
                    steps.add(
                        JourneyStep(
                            type = StepType.WALK,
                            fromStopName = g.clusters[curCluster].name,
                            toStopName = g.clusters[endC].name,
                            distanceMeters = meters,
                            durationMinutes = Math.max(1, (sec / 60).roundToInt())
                        )
                    )
                    curCluster = endC
                }
                "RIDE" -> {
                    val ri = e.routeIdx
                    var meters = 0.0; var sec = 0.0; var endC = curCluster; var hops = 0
                    while (i < path.size && path[i].first.type == "RIDE" && path[i].first.routeIdx == ri) {
                        meters += path[i].first.meters; sec += path[i].first.seconds; endC = path[i].second; hops++; i++
                    }
                    totalSec += sec; totalMeters += meters
                    val route = g.routes[ri]
                    steps.add(
                        JourneyStep(
                            type = StepType.RIDE,
                            routeNumber = route.number,
                            routeId = route.id,
                            routeColor = route.color,
                            fromStopName = g.clusters[curCluster].name,
                            toStopName = g.clusters[endC].name,
                            rideStopsCount = hops,
                            distanceMeters = meters,
                            durationMinutes = Math.max(1, (sec / 60).roundToInt())
                        )
                    )
                    curCluster = endC
                }
                else -> i++
            }
        }

        if (steps.none { it.type == StepType.RIDE }) return null
        val transfers = Math.max(0, boards - 1)
        return Journey(
            steps = steps,
            totalMinutes = Math.max(1, (totalSec / 60).roundToInt()),
            totalDistanceMeters = totalMeters,
            walkMeters = walkMeters,
            transfers = transfers
        )
    }
}
