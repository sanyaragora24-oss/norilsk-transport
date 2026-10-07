package com.example.data

import android.content.Context
import android.util.Log
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouter
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.FitnessOptions
import com.yandex.mapkit.transport.masstransit.PedestrianRouter
import com.yandex.mapkit.transport.masstransit.Route as MtRoute
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session as MtSession
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.runtime.Error
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class CachedPolyline(val points: List<SerializablePoint>)

@Serializable
data class SerializablePoint(val lat: Double, val lon: Double)

class RoadGeometryRepository(context: Context) {
    private val context = context.applicationContext
    private val drivingRouter: DrivingRouter =
        DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED)
    // Пешеходный роутер: его граф знает грунтовки/служебные дороги/тропы, которых
    // НЕТ в автомобильном графе. Автобусные служебные дороги у завода (НМЗ, АБК Южный)
    // как раз такие — авто-роутер их не видит и петляет/режет прямой. Используем
    // пешеходный роутер как фолбэк, когда авто-маршрут перегона уходит в объезд.
    private val pedestrianRouter: PedestrianRouter =
        TransportFactory.getInstance().createPedestrianRouter()
    // v3: сменили алгоритм (попарно + пешеходный фолбэк) -> сбрасываем старый кэш,
    // иначе старая геометрия с петлями/срезами переживёт обновление на устройстве.
    private val cacheDir = File(context.cacheDir, "road_geometry_v3")
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    suspend fun getRoadPolyline(routeId: String, stops: List<Point>): List<Point> {
        currentCoroutineContext().ensureActive()
        val safeRouteId = routeId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val cacheFile = File(cacheDir, "${safeRouteId.ifBlank { "route" }}.json")
        if (cacheFile.exists()) {
            try {
                val cached = json.decodeFromString<CachedPolyline>(cacheFile.readText())
                currentCoroutineContext().ensureActive()
                Log.d("RoadGeometry", "Cache hit for $routeId: ${cached.points.size} pts")
                return cached.points.map { Point(it.lat, it.lon) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RoadGeometry", "Cache read failed", e)
            }
        }

        // ПО ПАРАМ: дорогу строим отдельно для КАЖДОЙ пары соседних остановок
        // (точка -> точка). Сначала пробуем авто-роутер. Если он уходит в объезд
        // (длина дороги сильно больше прямой) или не нашёл маршрут -> пробуем
        // пешеходный роутер (он знает грунтовки/служебки автобусов). Берём тот
        // вариант, что короче и ближе к реальной дороге. Линия идёт ПО ДОРОГЕ.
        val fullPolyline = mutableListOf<Point>()
        var segmentsBuilt = 0
        var fallbacks = 0
        var pedUsed = 0

        for (i in 0 until (stops.size - 1)) {
            currentCoroutineContext().ensureActive()
            val a = stops[i]
            val b = stops[i + 1]
            val pair = listOf(a, b)
            val straight = distanceMeters(a, b)

            val drive = fetchDrivingSegment(pair)
            currentCoroutineContext().ensureActive()
            val driveLen = if (drive.size >= 2) polylineLengthMeters(drive) else Double.MAX_VALUE
            // Авто-маршрут "плохой", если его не нашли ИЛИ он заметно длиннее прямой
            // (объезд/петля). Порог мягкий, чтобы поймать петли у завода.
            val driveBad = drive.size < 2 || driveLen > straight * 1.5 + 150.0

            var seg: List<Point>
            if (driveBad) {
                val walk = fetchPedestrianSegment(pair)
                currentCoroutineContext().ensureActive()
                val walkLen = if (walk.size >= 2) polylineLengthMeters(walk) else Double.MAX_VALUE
                // Берём пешеходный, если он короче авто-объезда И при этом НЕ спрямляет
                // почти в прямую (защита: если авто-маршрут есть, а пешеходный почти
                // равен прямой — это "срез через поле", не настоящая дорога -> не берём).
                val walkIsStraightCut = drive.size >= 2 && walkLen < straight * 1.1
                seg = when {
                    walk.size >= 2 && walkLen <= driveLen && !walkIsStraightCut -> { pedUsed++; walk }
                    drive.size >= 2 -> drive
                    walk.size >= 2 -> { pedUsed++; walk }
                    else -> pair // оба не нашли -> прямая
                }
            } else {
                seg = drive
            }

            if (seg.size >= 2) segmentsBuilt++ else { fallbacks++; seg = pair }

            if (fullPolyline.isEmpty()) {
                fullPolyline.addAll(seg)
            } else {
                // стыкуем перегоны без дубля стартовой точки
                fullPolyline.addAll(seg.drop(1))
            }
        }

        Log.d(
            "RoadGeometry",
            "Route $routeId: segments=$segmentsBuilt, fallbacks=$fallbacks, pedestrian=$pedUsed. Pts=${fullPolyline.size}"
        )

        if (fullPolyline.isNotEmpty() && segmentsBuilt > 0) {
            currentCoroutineContext().ensureActive()
            try {
                val cached = CachedPolyline(fullPolyline.map { SerializablePoint(it.latitude, it.longitude) })
                cacheFile.writeText(json.encodeToString(cached))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RoadGeometry", "Cache write failed", e)
            }
        }
        currentCoroutineContext().ensureActive()
        return fullPolyline
    }

    private suspend fun fetchDrivingSegment(stops: List<Point>): List<Point> {
        val requestPoints = stops.mapIndexed { index, point ->
            RequestPoint(
                point,
                if (index == 0 || index == stops.lastIndex) RequestPointType.WAYPOINT else RequestPointType.VIAPOINT,
                null,
                null,
                null
            )
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                val session = drivingRouter.requestRoutes(
                    requestPoints,
                    DrivingOptions(),
                    VehicleOptions(),
                    object : DrivingSession.DrivingRouteListener {
                        override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
                            val points = if (routes.isNotEmpty()) routes[0].geometry.points else emptyList()
                            if (continuation.isActive) continuation.resume(points)
                        }

                        override fun onDrivingRoutesError(error: Error) {
                            Log.e("RoadGeometry", "Driving segment error: $error")
                            if (continuation.isActive) continuation.resume(emptyList())
                        }
                    }
                )
                continuation.invokeOnCancellation { session.cancel() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RoadGeometry", "Driving request failed", e)
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
    }

    private suspend fun fetchPedestrianSegment(stops: List<Point>): List<Point> {
        val requestPoints = stops.mapIndexed { index, point ->
            RequestPoint(
                point,
                if (index == 0 || index == stops.lastIndex) RequestPointType.WAYPOINT else RequestPointType.VIAPOINT,
                null,
                null,
                null
            )
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                val session = pedestrianRouter.requestRoutes(
                    requestPoints,
                    TimeOptions(),
                    RouteOptions(FitnessOptions(false, false)),
                    object : MtSession.RouteListener {
                        override fun onMasstransitRoutes(routes: MutableList<MtRoute>) {
                            val points = if (routes.isNotEmpty()) routes[0].geometry.points else emptyList()
                            if (continuation.isActive) continuation.resume(points)
                        }

                        override fun onMasstransitRoutesError(error: Error) {
                            Log.e("RoadGeometry", "Pedestrian segment error: $error")
                            if (continuation.isActive) continuation.resume(emptyList())
                        }
                    }
                )
                continuation.invokeOnCancellation { session.cancel() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RoadGeometry", "Pedestrian request failed", e)
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
    }

    private fun polylineLengthMeters(pts: List<Point>): Double {
        var sum = 0.0
        for (i in 0 until pts.size - 1) sum += distanceMeters(pts[i], pts[i + 1])
        return sum
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val la1 = Math.toRadians(a.latitude)
        val la2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }
}
