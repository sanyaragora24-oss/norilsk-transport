package com.example.ui.map

import android.app.Application
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ArrivalEstimator
import com.example.data.Bus
import com.example.data.RoadGeometryRepository
import com.example.data.Route
import com.example.data.RouteGeometryPolicy
import com.example.data.RoutePlanner
import com.example.data.RouteRepository
import com.example.data.Stop
import com.example.data.routeNumberKey
import com.example.data.compareRouteNumbers
import com.example.data.matchesTransitSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.example.data.local.AppTheme
import com.example.data.local.FavoritesRepository
import com.example.data.local.SettingsRepository
import com.example.data.local.WeatherAlertNotifier
import com.example.data.network.BusRepository
import com.example.data.network.WeatherRepository
import com.example.data.network.evaluateWeatherAlerts
import com.example.ui.components.NEARBY_STOPS_LIMIT_METERS
import com.example.ui.components.formatDistance
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.MapType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val ROAD_GEOMETRY_TIMEOUT_MS = 30_000L

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val routeRepository = RouteRepository(application)
    private val scheduleRepository = com.example.data.ScheduleRepository(application)
    private val weatherRepository = WeatherRepository()
    private val settingsRepository = SettingsRepository(application)
    private val favoritesRepository = FavoritesRepository(application)
    private val favoriteGroupsRepository = com.example.data.local.FavoriteGroupsRepository(application)
    private val busRepository = BusRepository(
        feedUrl = BuildConfig.VEHICLE_FEED_URL,
        resolveRouteId = { name -> resolveRouteIdByNumber(name) }
    )
    private val roadGeometryRepository = RoadGeometryRepository(application)
    private val savedJourneysRepository = com.example.data.local.SavedJourneysRepository(application)
    
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _mapCommands = MutableSharedFlow<MapCommand>()
    val mapCommands: SharedFlow<MapCommand> = _mapCommands.asSharedFlow()

    private var weatherJob: Job? = null
    private var mapLoadingJob: Job? = null
    private var routeGeometryJob: Job? = null
    private var journeyPlanningJob: Job? = null
    private var journeyGeometryJob: Job? = null
    private var busesJob: Job? = null
    private var locationFocusJob: Job? = null
    private val routeGeometryRequests = LatestRequestGuard()
    private val journeyPlanningRequests = LatestRequestGuard()
    private val journeyGeometryRequests = LatestRequestGuard()
    private var lastAutoReplanAtMs = 0L
    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkAvailability()
        override fun onLost(network: Network) = refreshNetworkAvailability()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refreshNetworkAvailability()
    }

    init {
        loadRoutes()
        startBusUpdates()
        observeSettings()
        startWeatherUpdates()
        startMapLoadingTimeout()
        observeNetworkAvailability()
        viewModelScope.launch {
            com.example.service.StopAlarmService.activeAlarmStopId.collect { stopId ->
                _uiState.update { it.copy(alarmStopId = stopId) }
            }
        }
    }

    private fun loadRoutes() {
        val routes = routeRepository.getRoutes()
        val schedules = scheduleRepository.getScheduleMap()
        _uiState.update {
            it.copy(
                routes = routes,
                filteredRoutes = routes,
                filteredStops = routeRepository.getAllStops(),
                scheduleByRouteId = schedules,
                scheduleDataDate = scheduleRepository.dataDate
            )
        }
    }

    // Демо-автобусы: реальные центральные маршруты Норильска (видны сразу у стартовой камеры),
    // двигаются по геометрии маршрута, чтобы было видно движение.
    private data class DemoBus(
        val id: String,
        val routeId: String,
        val plate: String,
        val accessible: Boolean,
        val points: List<Point>,
        var seg: Int,
        var t: Double,
        val dir: Int // +1 вперёд, -1 назад (для разнообразия)
    )

    private fun startDemoBusUpdates() {
        busesJob?.cancel()
        busesJob = viewModelScope.launch {
            // центральные маршруты рядом с Норильском (видны у стартовой камеры 69.355, 88.189)
            val demoRouteIds = listOf("2220:0", "2226:0", "2233:0", "2227:0", "2239:0", "2223:0")
            val plates = listOf("А101КР", "В202МН", "С303ОР", "Е404КХ", "К505МК", "М606ОР",
                "Н707РС", "О808ТУ", "Р909ХА", "С010МК", "Т111НН", "У212ОО")
            val demos = mutableListOf<DemoBus>()
            var pi = 0
            demoRouteIds.forEachIndexed { ri, rid ->
                val route = _uiState.value.routes.find { it.id == rid } ?: return@forEachIndexed
                val pts = route.polyline.ifEmpty { route.stops.map { it.location } }
                if (pts.size < 2) return@forEachIndexed
                // по 2 автобуса на маршрут, в разных точках и направлениях
                repeat(2) { k ->
                    val startSeg = (pts.size * (if (k == 0) 0.15 else 0.6)).toInt()
                        .coerceIn(0, pts.size - 2)
                    demos.add(
                        DemoBus(
                            id = "demo_${rid}_$k",
                            routeId = rid,
                            plate = plates[pi % plates.size],
                            accessible = (pi % 2 == 0),
                            points = pts,
                            seg = startSeg,
                            t = 0.0,
                            dir = if ((ri + k) % 2 == 0) 1 else -1
                        )
                    )
                    pi++
                }
            }

            if (demos.isEmpty()) return@launch

            // первый кадр сразу
            _uiState.update { it.copy(buses = demos.map { d -> d.toBus() }) }

            while (true) {
                delay(900)
                demos.forEach { d -> advanceDemo(d) }
                _uiState.update { it.copy(buses = demos.map { d -> d.toBus() }) }
                _uiState.value.selectedStop?.let { stop ->
                    _uiState.update {
                        it.copy(
                            arrivalsAtSelectedStop = computeArrivals(stop.id),
                            accessibleArrivalsAtSelectedStop = computeAccessibleArrivals(stop.id)
                        )
                    }
                }
            }
        }
    }

    private fun DemoBus.toBus(): Bus {
        val a = points[seg]
        val b = points[(seg + 1).coerceAtMost(points.size - 1)]
        val lat = a.latitude + (b.latitude - a.latitude) * t
        val lon = a.longitude + (b.longitude - a.longitude) * t
        return Bus(id, routeId, Point(lat, lon), plate = plate, isAccessible = accessible)
    }

    private fun advanceDemo(d: DemoBus) {
        d.t += 0.2
        if (d.t >= 1.0) {
            d.t = 0.0
            var next = d.seg + d.dir
            if (next >= d.points.size - 1) next = 0
            if (next < 0) next = d.points.size - 2
            d.seg = next.coerceIn(0, d.points.size - 2)
        }
    }

    private fun startBusUpdates() {
        if (BuildConfig.VEHICLE_FEED_URL.isBlank()) {
            if (BuildConfig.DEBUG) {
                _uiState.update { it.copy(busDataStatus = "Демонстрационные автобусы") }
                startDemoBusUpdates()
            } else {
                _uiState.update { it.copy(buses = emptyList(), busDataStatus = "GPS-данные автобусов пока недоступны") }
            }
            return
        }
        if (!BuildConfig.VEHICLE_FEED_URL.startsWith("https://", ignoreCase = true)) {
            _uiState.update { it.copy(buses = emptyList(), busDataStatus = "Источник GPS-данных должен использовать HTTPS") }
            return
        }
        _uiState.update { it.copy(busDataStatus = null) }
        busesJob?.cancel()
        busesJob = viewModelScope.launch {
            busRepository.busUpdates().collectLatest { buses ->
                _uiState.update {
                    it.copy(
                        buses = buses,
                        busDataStatus = if (buses.isEmpty()) "Нет свежих GPS-данных" else null
                    )
                }
                // если открыта остановка — пересчитываем прогноз прибытия
                _uiState.value.selectedStop?.let { stop ->
                    _uiState.update {
                        it.copy(
                            arrivalsAtSelectedStop = computeArrivals(stop.id),
                            accessibleArrivalsAtSelectedStop = computeAccessibleArrivals(stop.id)
                        )
                    }
                }
            }
        }
    }

    private fun resolveRouteIdByNumber(routeName: String): String? {
        val routes = _uiState.value.routes
        routes.firstOrNull { it.id.equals(routeName, ignoreCase = true) }?.let { return it.id }
        val candidates = routes.filter { it.number.equals(routeName.trim(), ignoreCase = true) }
        return when (candidates.size) {
            0 -> null
            1 -> candidates.single().id
            else -> routeNumberKey(candidates.first().number)
        }
    }

    private fun observeSettings() {
        settingsRepository.appTheme.onEach { theme ->
            _uiState.update { it.copy(appTheme = theme) }
        }.launchIn(viewModelScope)

        settingsRepository.mapType.onEach { type ->
            _uiState.update { it.copy(mapType = type) }
        }.launchIn(viewModelScope)

        favoritesRepository.favoriteStopIds.onEach { ids ->
            _uiState.update { it.copy(favoriteStopIds = ids) }
        }.launchIn(viewModelScope)

        favoritesRepository.favoriteRouteIds.onEach { ids ->
            _uiState.update { it.copy(favoriteRouteIds = ids) }
        }.launchIn(viewModelScope)

        favoriteGroupsRepository.groups.onEach { groups ->
            _uiState.update { it.copy(favoriteGroups = groups) }
        }.launchIn(viewModelScope)

        savedJourneysRepository.savedJourneys.onEach { journeys ->
            _uiState.update { it.copy(savedJourneys = journeys) }
        }.launchIn(viewModelScope)
    }

    /** Сохранить текущий построенный маршрут А→Б в избранное. */
    fun saveCurrentJourney() {
        val from = _uiState.value.plannerFrom ?: return
        val to = _uiState.value.plannerTo ?: return
        viewModelScope.launch {
            savedJourneysRepository.add(
                com.example.data.local.SavedJourney(from.id, to.id, from.name, to.name)
            )
        }
    }

    fun removeSavedJourney(j: com.example.data.local.SavedJourney) {
        viewModelScope.launch { savedJourneysRepository.remove(j.fromStopId, j.toStopId) }
    }

    /** Открыть сохранённый маршрут: подставить остановки и сразу построить. */
    fun applySavedJourney(j: com.example.data.local.SavedJourney) {
        val all = _uiState.value.routes.flatMap { it.stops }
        val from = all.find { it.id == j.fromStopId }
        val to = all.find { it.id == j.toStopId }
        if (from != null && to != null) {
            _uiState.update {
                it.copy(
                    plannerFrom = from,
                    plannerTo = to,
                    plannerFromUsesCurrentLocation = false,
                    plannerJourney = null,
                    plannerError = null
                )
            }
            buildJourney()
        }
    }

    /** Текущая пара остановок уже в избранном? */
    fun isCurrentJourneySaved(): Boolean {
        val from = _uiState.value.plannerFrom ?: return false
        val to = _uiState.value.plannerTo ?: return false
        return _uiState.value.savedJourneys.any { it.fromStopId == from.id && it.toStopId == to.id }
    }

    fun toggleFavoriteStop(stopId: String) {
        viewModelScope.launch { favoritesRepository.toggleStop(stopId) }
    }

    fun toggleFavoriteRoute(routeId: String) {
        viewModelScope.launch { favoritesRepository.toggleRoute(routeId) }
    }

    // --- Группы избранного ---
    fun createFavoriteGroup(name: String) {
        viewModelScope.launch { favoriteGroupsRepository.createGroup(name) }
    }

    fun renameFavoriteGroup(id: String, name: String) {
        viewModelScope.launch { favoriteGroupsRepository.renameGroup(id, name) }
    }

    fun deleteFavoriteGroup(id: String) {
        viewModelScope.launch { favoriteGroupsRepository.deleteGroup(id) }
    }

    fun toggleRouteInGroup(groupId: String, routeId: String) {
        viewModelScope.launch { favoriteGroupsRepository.toggleRouteInGroup(groupId, routeId) }
    }

    fun toggleStopInGroup(groupId: String, stopId: String) {
        viewModelScope.launch { favoriteGroupsRepository.toggleStopInGroup(groupId, stopId) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchText = query) }
        filterResults()
    }

    fun setSearchCategory(category: SearchCategory) {
        _uiState.update { it.copy(searchCategory = category) }
        filterResults()
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchText = "") }
        filterResults()
    }

    private fun filterResults() {
        val query = _uiState.value.searchText
        if (query.isBlank()) {
            // Без запроса показываем полные списки: пустая вкладка «Остановки»
            // выглядела как сломанный экран, а не как приглашение искать.
            _uiState.update {
                it.copy(
                    filteredRoutes = it.routes,
                    filteredStops = routeRepository.getAllStops()
                )
            }
            return
        }

        when (_uiState.value.searchCategory) {
            SearchCategory.ROUTES -> {
                val filtered = _uiState.value.routes.filter {
                    matchesTransitSearch(query, it.number, it.origin, it.destination)
                }
                _uiState.update { it.copy(filteredRoutes = filtered) }
            }
            SearchCategory.STOPS -> {
                val allStops = routeRepository.getAllStops()
                val filtered = allStops.filter { matchesTransitSearch(query, it.name) }
                _uiState.update { it.copy(filteredStops = filtered) }
            }
        }
    }

    fun onUserLocationChanged(point: Point) {
        val previous = _uiState.value
        _uiState.update {
            it.copy(
                userLocation = point,
                isLocatingUser = false,
                locationFixTimedOut = false
            )
        }
        if (previous.isLocatingUser) {
            locationFocusJob?.cancel()
            viewModelScope.launch { _mapCommands.emit(MapCommand.FocusUserLocation(point)) }
        }
        updateNearbyStops(point)
        updateDisplayedJourneyStart(point)

        if (previous.plannerFromUsesCurrentLocation) {
            val nearest = nearestStop(point)
            if (nearest != null && nearest.id != previous.plannerFrom?.id) {
                val currentDistance = previous.plannerFrom?.let { distanceMeters(point, it.location) }
                    ?: Double.MAX_VALUE
                val candidateDistance = distanceMeters(point, nearest.location)
                val now = SystemClock.elapsedRealtime()
                // GPS noise near two adjacent stops must not rebuild the whole journey on every fix.
                if (!AutoReplanPolicy.shouldReplan(
                        currentDistanceMeters = currentDistance,
                        candidateDistanceMeters = candidateDistance,
                        nowMs = now,
                        lastReplanAtMs = lastAutoReplanAtMs
                    )
                ) return

                val shouldRebuild = previous.plannerTo != null &&
                    (previous.plannerJourney != null || previous.plannerError != null || previous.isPlanning)
                val shouldRedraw = shouldRebuild && previous.journeySegments.isNotEmpty()
                lastAutoReplanAtMs = now
                _uiState.update {
                    it.copy(
                        plannerFrom = nearest,
                        plannerJourney = null,
                        plannerVariants = emptyList(),
                        plannerError = null
                    )
                }
                if (shouldRebuild) buildJourney(redrawOnSuccess = shouldRedraw)
            }
        }
    }

    private fun nearestStop(point: Point): Stop? = routeRepository.getAllStops().minByOrNull { stop ->
        distanceMeters(point, stop.location)
    }

    /** Keep the walking leg from the user to the boarding stop current while they move. */
    private fun updateDisplayedJourneyStart(userPoint: Point) {
        val state = _uiState.value
        val boardingStopId = state.journeyBoardingStopId ?: return
        val boardingStop = routeRepository.getAllStops().firstOrNull { it.id == boardingStopId } ?: return
        val distance = distanceMeters(userPoint, boardingStop.location)
        val startSegment = JourneySegmentUi(
            points = listOf(userPoint, boardingStop.location),
            color = 0xFF9E9E9E,
            isWalk = true,
            isUserToBoarding = true
        )
        val otherSegments = state.journeySegments.filterNot { it.isUserToBoarding }
        val otherMarkers = state.journeyMarkers.filterNot { marker ->
            marker.stopId == boardingStopId && marker.label.startsWith("До остановки")
        }
        _uiState.update {
            it.copy(
                journeySegments = listOf(startSegment) + otherSegments,
                journeyMarkers = otherMarkers + JourneyMarkerUi(
                    boardingStop.location,
                    "До остановки ~${formatDistance(distance)} пешком",
                    boardingStop.id
                )
            )
        }
    }

    fun focusUserLocation() {
        // при нажатии «Где я» снова показываем шторку ближайших остановок
        _uiState.update { it.copy(isNearbyStopsCollapsed = false) }
        val point = _uiState.value.userLocation
        if (point == null) {
            locationFocusJob?.cancel()
            _uiState.update {
                it.copy(isLocatingUser = true, locationFixTimedOut = false)
            }
            locationFocusJob = viewModelScope.launch {
                delay(LOCATION_FIX_TIMEOUT_MS)
                if (_uiState.value.userLocation == null && _uiState.value.isLocatingUser) {
                    _uiState.update {
                        it.copy(isLocatingUser = false, locationFixTimedOut = true)
                    }
                }
            }
            return
        }
        viewModelScope.launch {
            _mapCommands.emit(MapCommand.FocusUserLocation(point))
        }
    }

    fun consumeLocationFixTimeout() {
        _uiState.update { it.copy(locationFixTimedOut = false) }
    }

    /** Закрыть шторку «Ближайшие остановки» (крестик). */
    fun collapseNearbyStops() {
        _uiState.update { it.copy(isNearbyStopsCollapsed = true) }
    }

    /** Вкл/выкл подсказки «Ближайшие остановки» в «Слои и настройки». */
    fun toggleNearbyStops() {
        _uiState.update { it.copy(areNearbyStopsEnabled = !it.areNearbyStopsEnabled) }
    }

    fun zoomIn() {
        viewModelScope.launch {
            _mapCommands.emit(MapCommand.ZoomIn)
        }
    }

    fun zoomOut() {
        viewModelScope.launch {
            _mapCommands.emit(MapCommand.ZoomOut)
        }
    }

    private fun updateNearbyStops(userPoint: Point) {
        val allStops = routeRepository.getAllStops()
        val nearby = allStops.map { stop ->
            val results = FloatArray(1)
            Location.distanceBetween(
                userPoint.latitude, userPoint.longitude,
                stop.location.latitude, stop.location.longitude,
                results
            )
            val distance = results[0].toDouble()
            val routes = routeRepository.getRoutesForStop(stop.id)
            NearbyStop(stop, distance, routes)
        }
        .sortedBy { it.distanceMeters }
        .take(15)

        // Если пользователь вне Норильска, список «ближайших» остановок теряет
        // смысл: показывать остановку в 7000 км как ближайшую — вводить в
        // заблуждение. Честнее сказать, что мы его не обслуживаем.
        val outsideCity = nearby.firstOrNull()?.let { it.distanceMeters > NEARBY_STOPS_LIMIT_METERS } ?: true

        _uiState.update {
            it.copy(
                nearbyStops = if (outsideCity) emptyList() else nearby,
                isOutsideServiceArea = outsideCity
            )
        }
    }

    fun onCameraMoved(zoom: Float) {
        _uiState.update { it.copy(cameraZoom = zoom) }
    }

    fun onMapLoaded() {
        Log.d("MapViewModel", "Map loaded, cancelling timeout")
        _uiState.update { it.copy(isMapLoaded = true, mapLoadTimedOut = false) }
        mapLoadingJob?.cancel()
    }

    private fun startMapLoadingTimeout() {
        mapLoadingJob?.cancel()
        mapLoadingJob = viewModelScope.launch {
            delay(MAP_LOAD_TIMEOUT_MS)
            if (!_uiState.value.isMapLoaded) {
                _uiState.update { it.copy(mapLoadTimedOut = true) }
            }
        }
    }

    private fun observeNetworkAvailability() {
        refreshNetworkAvailability()
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        } catch (_: RuntimeException) {
            _uiState.update { it.copy(isNetworkAvailable = false) }
        }
    }

    private fun refreshNetworkAvailability() {
        val network = connectivityManager?.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        val available = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        _uiState.update { it.copy(isNetworkAvailable = available) }
    }

    fun selectRoute(route: Route?) {
        cancelJourneyGeometry()
        if (route == null) cancelRouteGeometry()
        _uiState.update { 
            it.copy(
                selectedRoute = route, 
                selectedRoutePolyline = route?.polyline ?: emptyList(),
                highlightedStopId = null,
                journeySegments = emptyList(),
                journeyMarkers = emptyList(),
                journeyBoardingStopId = null,
                isBuildingRoads = false
            ) 
        }
        if (route != null && !RouteGeometryPolicy.useVerifiedAssetGeometry(route)) {
            buildRoadPolyline(route)
        } else if (route != null) {
            // 31/31Э contain industrial service-road sections.  The bundled
            // geometry is verified; MapKit's public road graph has previously
            // returned a 100+ km detour here, so do not replace it dynamically.
            roadGeometryRepository.discardCachedPolyline(route.id)
            Log.d("MapViewModel", "Using verified asset geometry for ${route.id}")
        }
    }

    private fun buildRoadPolyline(route: Route) {
        val requestId = routeGeometryRequests.next()
        routeGeometryJob?.cancel()
        routeGeometryJob = viewModelScope.launch {
            _uiState.update { it.copy(isBuildingRoads = true) }
            val roadPoints = withTimeoutOrNull(ROAD_GEOMETRY_TIMEOUT_MS) {
                roadGeometryRepository.getRoadPolyline(route.id, route.stops.map { it.location })
            }.orEmpty()
            routeGeometryRequests.ensureCurrent(requestId)
            if (roadPoints.isNotEmpty()) {
                _uiState.update { it.copy(selectedRoutePolyline = roadPoints, isBuildingRoads = false) }
            } else {
                _uiState.update { it.copy(isBuildingRoads = false) }
                Log.d("MapViewModel", "Fallback to stop-to-stop polyline for ${route.number}")
            }
        }
    }

    fun selectStop(stop: Stop?) {
        val routes = stop?.let { routeRepository.getRoutesForStop(it.id) } ?: emptyList()
        val arrivals = if (stop != null) computeArrivals(stop.id) else emptyMap()
        val accessible = if (stop != null) computeAccessibleArrivals(stop.id) else emptySet()
        _uiState.update {
            it.copy(
                selectedStop = stop,
                routesAtSelectedStop = routes,
                arrivalsAtSelectedStop = arrivals,
                accessibleArrivalsAtSelectedStop = accessible
            )
        }
    }

    /** Оценка прибытия по каждому маршруту, проходящему через остановку (routeId -> минуты). */
    private fun computeArrivals(stopId: String): Map<String, Int?> {
        val buses = _uiState.value.buses
        val routes = _uiState.value.routes
        val schedules = _uiState.value.scheduleByRouteId
        val result = mutableMapOf<String, Int?>()
        routeRepository.getRoutesForStop(stopId).forEach { summary ->
            val route = routes.find { it.id == summary.id }
            // 1) сначала пробуем живой прогноз по GPS, 2) иначе — по расписанию (офлайн)
            val live = route?.let { ArrivalEstimator.estimateMinutes(it, stopId, buses) }
            result[summary.id] = live ?: route?.let {
                com.example.data.ScheduleEstimator.minutesUntilNext(it, schedules[summary.id], stopId)
            }
        }
        return result
    }

    /**
     * Маршруты, по которым к остановке едет низкопольный автобус.
     *
     * Признак берётся из фида перевозчика: без живых данных он неизвестен, и
     * тогда значок доступности не показывается вовсе — обещать пандус, которого
     * может не быть, хуже, чем не сказать ничего.
     */
    private fun computeAccessibleArrivals(stopId: String): Set<String> {
        val buses = _uiState.value.buses
        if (buses.isEmpty()) return emptySet()
        val routes = _uiState.value.routes
        return routeRepository.getRoutesForStop(stopId)
            .mapNotNull { summary ->
                val route = routes.find { it.id == summary.id } ?: return@mapNotNull null
                val accessible = buses.any { it.routeId == route.id && it.isAccessible }
                summary.id.takeIf { accessible }
            }
            .toSet()
    }

    /** Номера маршрутов через остановку — для списка поиска. */
    fun routeNumbersForStop(stopId: String): List<String> =
        routeRepository.getRoutesForStop(stopId)
            .map { it.number }
            .distinct()
            .sortedWith(Comparator(::compareRouteNumbers))

    fun highlightStop(stopId: String?) {
        _uiState.update { it.copy(highlightedStopId = stopId) }
    }

    fun onStopTapped(stop: Stop) {
        selectStopAndFocus(stop)
    }

    fun focusStop(stop: Stop) {
        highlightStop(stop.id)
        viewModelScope.launch {
            _mapCommands.emit(MapCommand.FocusStop(stop.location))
        }
    }
    
    fun selectStopAndFocus(stop: Stop) {
        selectStop(stop)
        focusStop(stop)
    }

    // --- Оповещение «скоро ваша остановка» (будильник по GPS) ---
    /** Переключатель режима: только сигнал ↔ сигнал + голос. */
    fun setAlarmVoice(enabled: Boolean) {
        _uiState.update { it.copy(alarmVoice = enabled) }
    }

    /** Включить будильник на остановку: фоновый сервис разбудит при подъезде (~350 м). */
    fun armStopAlarm(stop: Stop) {
        com.example.service.StopAlarmService.start(
            getApplication(),
            stop.name,
            stop.location.latitude,
            stop.location.longitude,
            350.0,
            _uiState.value.alarmVoice,
            stop.id
        )
    }

    /** Выключить будильник. */
    fun cancelStopAlarm() {
        com.example.service.StopAlarmService.stop(getApplication())
        _uiState.update { it.copy(alarmStopId = null) }
    }

    // --- Превью оповещения (кнопка «Проверить») ---
    private var previewTts: TextToSpeech? = null
    private var previewTtsReady = false
    private var pendingPreviewPhrase: String? = null

    private fun ensurePreviewTts() {
        if (previewTts != null) return
        previewTts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val languageResult = try {
                    previewTts?.setLanguage(Locale.forLanguageTag("ru-RU"))
                        ?: TextToSpeech.LANG_NOT_SUPPORTED
                } catch (_: Exception) {
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
                previewTtsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (previewTtsReady) {
                    pendingPreviewPhrase?.let { speakPreview(it); pendingPreviewPhrase = null }
                } else {
                    pendingPreviewPhrase = null
                }
            }
        }
    }

    private fun speakPreview(text: String) {
        if (previewTtsReady) {
            try { previewTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview") } catch (_: Exception) {}
        } else {
            pendingPreviewPhrase = text
            ensurePreviewTts()
        }
    }

    /** Кнопка «Проверить»: проигрывает сигнал, а при включённом голосе — ещё и озвучку, как будет в поездке. */
    fun testAlarm(stop: Stop) {
        val app = getApplication<Application>()
        com.example.data.local.StopAlarmNotifier.createChannel(app)
        com.example.data.local.StopAlarmNotifier.notifyApproaching(app, stop.name, 350)
        if (_uiState.value.alarmVoice) {
            speakPreview("Подъезжаете к остановке ${stop.name}. Готовьтесь к выходу.")
        }
    }

    // --- Планировщик поездки А→Б с пересадками ---
    fun setPlannerFrom(stop: Stop?) {
        cancelJourneyPlanning()
        _uiState.update {
            it.copy(
                plannerFrom = stop,
                plannerFromUsesCurrentLocation = false,
                plannerJourney = null,
                plannerError = null,
                plannerVariants = emptyList(),
                isPlanning = false
            )
        }
    }

    /** Use the closest boarding stop and keep it current as the user moves. */
    fun setPlannerFromCurrentLocation() {
        val point = _uiState.value.userLocation
        val nearest = point?.let(::nearestStop)
        if (nearest == null) {
            _uiState.update { it.copy(plannerError = "Сначала включите геолокацию кнопкой «Где я»") }
            return
        }
        cancelJourneyPlanning()
        _uiState.update {
            it.copy(
                plannerFrom = nearest,
                plannerFromUsesCurrentLocation = true,
                plannerJourney = null,
                plannerError = null,
                plannerVariants = emptyList(),
                isPlanning = false
            )
        }
    }

    fun setPlannerTo(stop: Stop?) {
        cancelJourneyPlanning()
        _uiState.update {
            it.copy(
                plannerTo = stop,
                plannerJourney = null,
                plannerError = null,
                plannerVariants = emptyList(),
                isPlanning = false
            )
        }
    }

    fun swapPlannerEndpoints() {
        cancelJourneyPlanning()
        _uiState.update {
            it.copy(
                plannerFrom = it.plannerTo,
                plannerTo = it.plannerFrom,
                plannerFromUsesCurrentLocation = false,
                plannerJourney = null,
                plannerError = null,
                plannerVariants = emptyList(),
                isPlanning = false
            )
        }
    }

    fun clearPlanner() {
        cancelJourneyPlanning()
        cancelJourneyGeometry()
        _uiState.update {
            it.copy(
                plannerFrom = null, plannerTo = null, plannerJourney = null,
                plannerFromUsesCurrentLocation = false,
                plannerError = null, isPlanning = false, plannerVariants = emptyList(),
                selectedVariant = 0,
                // сбрасываем и нарисованный на карте маршрут, чтобы можно было строить новый «с нуля»
                journeySegments = emptyList(),
                journeyMarkers = emptyList(),
                journeyBoardingStopId = null,
                isBuildingRoads = false
            )
        }
    }

    /** Выбрать вариант поездки (вкладки в планировщике). */
    fun selectVariant(index: Int) {
        val v = _uiState.value.plannerVariants
        if (index in v.indices) _uiState.update { it.copy(selectedVariant = index, plannerJourney = v[index]) }
    }

    fun buildJourney(redrawOnSuccess: Boolean = false) {
        val planningState = _uiState.value
        val from = planningState.plannerFrom
        val to = planningState.plannerTo
        val gpsOrigin = planningState.userLocation.takeIf { planningState.plannerFromUsesCurrentLocation }
        if (from == null || to == null) {
            _uiState.update { it.copy(plannerError = "Выберите пункты «Откуда» и «Куда»") }
            return
        }
        if (from.id == to.id) {
            _uiState.update { it.copy(plannerError = "Это одна и та же остановка") }
            return
        }
        val requestId = journeyPlanningRequests.next()
        journeyPlanningJob?.cancel()
        journeyPlanningJob = viewModelScope.launch {
            _uiState.update { it.copy(isPlanning = true, plannerError = null, plannerJourney = null, plannerVariants = emptyList()) }
            try {
                val variants = withContext(Dispatchers.Default) {
                    if (gpsOrigin != null) RoutePlanner.planVariantsFromLocation(planningState.routes, gpsOrigin, to)
                    else RoutePlanner.planVariants(planningState.routes, from, to)
                }
                journeyPlanningRequests.ensureCurrent(requestId)
                if (variants.isEmpty()) {
                    _uiState.update { it.copy(isPlanning = false, plannerError = "Маршрут не найден. Попробуйте соседние остановки.") }
                } else {
                    _uiState.update { it.copy(isPlanning = false, plannerJourney = variants.first(), plannerVariants = variants, selectedVariant = 0) }
                    if (redrawOnSuccess) showJourneyOnMap()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A superseded planner may finish through a non-cooperative API
                // and fail with an ordinary exception after cancellation. Do not
                // let that obsolete request overwrite the current planner state.
                journeyPlanningRequests.ensureCurrent(requestId)
                // не оставляем кнопку «висеть» в состоянии загрузки при ошибке планировщика
                Log.e("MapViewModel", "buildJourney failed", e)
                _uiState.update { it.copy(isPlanning = false, plannerError = "Не удалось построить маршрут. Попробуйте ещё раз.") }
            }
        }
    }

    /** Показать на карте первый автобусный участок маршрута. */
    fun showJourneyRouteOnMap(routeId: String?) {
        val route = _uiState.value.routes.find { it.id == routeId } ?: return
        selectRoute(route)
    }

    private fun normalizeName(name: String): String =
        name.lowercase().replace(Regex("[^a-zа-яё0-9]+"), " ").trim()

    /**
     * Показать ВЕСЬ построенный маршрут А→Б на карте (как в Тюменском):
     *  - автобусные участки — цветом своего маршрута,
     *  - пешие — серым,
     *  - точки Посадка / Пересадка / Выход подписаны на карте,
     *  - камера сама подгоняется под весь маршрут.
     */
    fun showJourneyOnMap() {
        val journey = _uiState.value.plannerJourney ?: return
        val routes = _uiState.value.routes
        val userLoc = _uiState.value.userLocation

        // карта название-остановки -> Stop (координата + id для кликабельных меток)
        val stopByName = HashMap<String, Stop>()
        routes.forEach { r -> r.stops.forEach { stopByName.putIfAbsent(normalizeName(it.name), it) } }

        cancelRouteGeometry()
        val requestId = journeyGeometryRequests.next()
        journeyGeometryJob?.cancel()
        journeyGeometryJob = viewModelScope.launch {
            _uiState.update { it.copy(isBuildingRoads = true) }

            val segments = mutableListOf<JourneySegmentUi>()
            val markers = mutableListOf<JourneyMarkerUi>()
            val allPoints = mutableListOf<Point>()

            val rideSteps = journey.steps.count { it.type == RoutePlanner.StepType.RIDE }
            var rideIndex = 0
            var lastPoint: Point? = null
            var firstBoardStop: Stop? = null

            for (step in journey.steps) {
                journeyGeometryRequests.ensureCurrent(requestId)
                val fromKey = normalizeName(step.fromStopName)
                val toKey = normalizeName(step.toStopName)
                if (step.type == RoutePlanner.StepType.RIDE) {
                    val route = routes.find { it.id == step.routeId }
                    val fromStop = stopByName[fromKey]
                    val toStop = stopByName[toKey]

                    // остановки участка по порядку следования маршрута
                    val stopSeg: List<Stop> = if (route != null) {
                        val stops = route.stops
                        val fromIdx = stops.indexOfFirst { normalizeName(it.name) == fromKey }
                        val toIdx = stops.indexOfLast { normalizeName(it.name) == toKey }
                        if (fromIdx in 0..toIdx && toIdx >= 0) stops.subList(fromIdx, toIdx + 1)
                        else listOfNotNull(fromStop, toStop)
                    } else listOfNotNull(fromStop, toStop)

                    if (stopSeg.size >= 2) {
                        // привязка к дорогам (Yandex DrivingRouter, с кэшем); фолбэк — по остановкам
                        val raw = stopSeg.map { it.location }
                        val safeKey = "journey_${step.routeId}_${fromKey}_${toKey}"
                            .replace(Regex("[^A-Za-zА-Яа-я0-9]+"), "_")
                        val roadPts = try {
                            withTimeoutOrNull(ROAD_GEOMETRY_TIMEOUT_MS) {
                                roadGeometryRepository.getRoadPolyline(safeKey, raw)
                            }.orEmpty()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                        journeyGeometryRequests.ensureCurrent(requestId)
                        val snapped = if (roadPts.size >= 2) roadPts else raw
                        // 1) убираем «паразитные» петли DrivingRouter между близкими
                        //    остановками (напр. Стройбаза→АБК Южный длинной объездной
                        //    вокруг НМЗ «как будто до ЦБК и обратно») — заменяем прямой;
                        val deLooped = removeStopDetours(snapped, raw)
                        // 2) обрезаем «перелёт» дороги за остановкой выхода: для конечных
                        //    точек (напр. Хвосты) Yandex DrivingRouter иногда тянет линию
                        //    дальше точки и разворачивается — режем на подходе к выходу.
                        val segPoints = clipPolylineAtTarget(deLooped, raw.last())

                        segments.add(JourneySegmentUi(segPoints, step.routeColor, isWalk = false))
                        allPoints.addAll(segPoints)

                        val rn = step.routeNumber ?: route?.number
                        val label = if (rideIndex == 0) "Посадка: ${step.fromStopName} • марш. ${rn ?: "?"}"
                                    else "Пересадка: ${step.fromStopName} → марш. ${rn ?: "?"}"
                        markers.add(JourneyMarkerUi(segPoints.first(), label, fromStop?.id))
                        if (rideIndex == 0) firstBoardStop = fromStop
                        lastPoint = segPoints.last()
                        rideIndex++
                        if (rideIndex == rideSteps) {
                            markers.add(JourneyMarkerUi(segPoints.last(), "Выход: ${step.toStopName}", toStop?.id))
                        }
                    }
                } else {
                    // пеший участок между остановками — серым
                    val a = lastPoint ?: stopByName[fromKey]?.location
                    val b = stopByName[toKey]?.location
                    if (a != null && b != null) {
                        segments.add(JourneySegmentUi(listOf(a, b), 0xFF9E9E9E, isWalk = true))
                        allPoints.add(a); allPoints.add(b)
                        lastPoint = b
                    }
                }
            }

            // сшиваем сегменты: если конец одного и начало следующего не совпадают
            // (напр. пересадка между близкими, но разными остановками) — добавляем серую перемычку,
            // чтобы на карте не было видимых разрывов между участками
            if (segments.size >= 2) {
                val stitched = mutableListOf<JourneySegmentUi>()
                segments.forEachIndexed { i, seg ->
                    if (i > 0 && stitched.isNotEmpty() && seg.points.isNotEmpty()) {
                        val prevEnd = stitched.last().points.last()
                        val curStart = seg.points.first()
                        if (distanceMeters(prevEnd, curStart) > 30.0) {
                            stitched.add(JourneySegmentUi(listOf(prevEnd, curStart), 0xFF9E9E9E, isWalk = true))
                        }
                    }
                    stitched.add(seg)
                }
                segments.clear(); segments.addAll(stitched)
            }

            // путь от ГЕОЛОКАЦИИ пользователя до остановки посадки — пунктиром + расстояние
            if (userLoc != null && firstBoardStop != null) {
                val d = distanceMeters(userLoc, firstBoardStop.location)
                segments.add(
                    0,
                    JourneySegmentUi(
                        listOf(userLoc, firstBoardStop.location),
                        0xFF9E9E9E,
                        isWalk = true,
                        isUserToBoarding = true
                    )
                )
                allPoints.add(userLoc)
                markers.add(JourneyMarkerUi(firstBoardStop.location, "До остановки ~${formatDistance(d)} пешком", firstBoardStop.id))
            }

            journeyGeometryRequests.ensureCurrent(requestId)
            _uiState.update {
                it.copy(
                    selectedRoute = null,
                    selectedRoutePolyline = emptyList(),
                    highlightedStopId = null,
                    journeySegments = segments,
                    journeyMarkers = markers,
                    journeyBoardingStopId = firstBoardStop?.id,
                    isBuildingRoads = false
                )
            }
            journeyGeometryRequests.ensureCurrent(requestId)
            if (allPoints.size >= 2) _mapCommands.emit(MapCommand.FocusPoints(allPoints))
        }
    }

    private fun cancelRouteGeometry() {
        routeGeometryRequests.invalidate()
        routeGeometryJob?.cancel()
        routeGeometryJob = null
    }

    private fun cancelJourneyPlanning() {
        journeyPlanningRequests.invalidate()
        journeyPlanningJob?.cancel()
        journeyPlanningJob = null
    }

    private fun cancelJourneyGeometry() {
        journeyGeometryRequests.invalidate()
        journeyGeometryJob?.cancel()
        journeyGeometryJob = null
    }

    /**
     * Обрезает дорожную полилинию ровно на ближайшем подходе к точке [target]
     * (остановка выхода). Убирает «перелёт»/разворот, который Yandex DrivingRouter
     * иногда добавляет за конечной точкой. Берём ПЕРВЫЙ индекс, где расстояние до
     * цели близко к минимуму (+допуск), и отбрасываем всё, что идёт после него.
     * Реальные промежуточные заходы (напр. Стройбаза перед АБК Южный) не режутся:
     * там минимум приходится на самый конец, поэтому линия сохраняется целиком.
     */
    private fun clipPolylineAtTarget(
        points: List<Point>,
        target: Point,
        toleranceMeters: Double = 30.0
    ): List<Point> {
        if (points.size < 3) return points
        var minDist = Double.MAX_VALUE
        points.forEach { p ->
            val d = distanceMeters(p, target)
            if (d < minDist) minDist = d
        }
        val cutIdx = points.indexOfFirst { distanceMeters(it, target) <= minDist + toleranceMeters }
        return if (cutIdx in 1 until points.size - 1) points.subList(0, cutIdx + 1).toList()
               else points
    }

    /**
     * Убирает «паразитные» дорожные петли DrivingRouter между БЛИЗКИМИ остановками.
     * Пример: Стройбаза → АБК Южный всего ~580 м по прямой, но машинный маршрутизатор
     * Яндекса гонит длинной объездной вокруг НМЗ (через зону Завода Тисма/ЦБК) и
     * возвращается — на карте выходит огромная петля «как будто доехал до ЦБК и обратно».
     * Реальный автобус так не ездит (служебная дорога, которой нет в авто-графе).
     *
     * Алгоритм: для каждой пары соседних остановок находим её участок в полилинии и
     * сравниваем длину дороги с прямым расстоянием. Если дорога в разы длиннее на
     * коротком перегоне — это артефакт, заменяем участок прямой между остановками.
     * Нормальные перегоны и реальные «усы» (длина дороги ≈ прямой) не трогаем.
     */
    // ВНИМАНИЕ: после перехода на ПОПАРНУЮ геометрию (RoadGeometryRepository строит
    // дорогу по каждой паре остановок отдельно) петель почти не бывает, а реальные
    // изгибы дороги нужно СОХРАНЯТЬ. Поэтому пороги КОНСЕРВАТИВНЫЕ — срезаем только
    // совсем грубые петли (дорога в разы длиннее прямой / уходит вбок сотни метров),
    // нормальные повороты не трогаем.
    private fun removeStopDetours(
        road: List<Point>,
        stops: List<Point>,
        ratioThreshold: Double = 4.0,
        maxStraightMeters: Double = 1500.0,
        minExtraMeters: Double = 800.0,
        maxDeviationMeters: Double = 800.0
    ): List<Point> {
        if (road.size < 3 || stops.size < 2) return road
        // ближайший индекс полилинии для каждой остановки (монотонно вперёд)
        val anchorIdx = IntArray(stops.size)
        var searchFrom = 0
        for (s in stops.indices) {
            var best = searchFrom
            var bestD = Double.MAX_VALUE
            for (j in searchFrom until road.size) {
                val d = distanceMeters(road[j], stops[s])
                if (d < bestD) { bestD = d; best = j }
            }
            anchorIdx[s] = best
            searchFrom = best
        }
        val result = mutableListOf(road[0])
        for (s in 0 until stops.size - 1) {
            val a = anchorIdx[s]
            val b = anchorIdx[s + 1]
            if (b <= a) { result.add(stops[s + 1]); continue }
            val straight = distanceMeters(stops[s], stops[s + 1])
            var roadLen = 0.0
            for (j in a until b) roadLen += distanceMeters(road[j], road[j + 1])
            // макс. поперечное отклонение дороги от прямой остановка→остановка:
            // «раздутая» петля вокруг завода уходит далеко вбок и возвращается —
            // этот признак ловит артефакт независимо от длины перегона.
            var maxDev = 0.0
            for (j in a..b) {
                val dev = pointToSegmentMeters(road[j], stops[s], stops[s + 1])
                if (dev > maxDev) maxDev = dev
            }
            val lengthArtifact = roadLen > straight * ratioThreshold &&
                (roadLen - straight) > minExtraMeters
            val bulgeArtifact = maxDev > maxDeviationMeters && roadLen > straight * 2.5
            val isArtifact = straight <= maxStraightMeters && (lengthArtifact || bulgeArtifact)
            Log.d(
                "JOURNEY_DETOUR",
                "leg ${s}->${s + 1} straight=${straight.toInt()} roadLen=${roadLen.toInt()} " +
                    "ratio=${if (straight > 0) "%.2f".format(roadLen / straight) else "-"} " +
                    "maxDev=${maxDev.toInt()} artifact=$isArtifact"
            )
            if (isArtifact) {
                result.add(stops[s + 1])
            } else {
                for (j in (a + 1)..b) result.add(road[j])
            }
        }
        return if (result.size >= 2) result else road
    }

    /** Кратчайшее расстояние (м) от точки [p] до отрезка [a]–[b] на плоскости (локально). */
    private fun pointToSegmentMeters(p: Point, a: Point, b: Point): Double {
        val latRef = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * Math.cos(latRef)
        val ax = a.longitude * mPerDegLon; val ay = a.latitude * mPerDegLat
        val bx = b.longitude * mPerDegLon; val by = b.latitude * mPerDegLat
        val px = p.longitude * mPerDegLon; val py = p.latitude * mPerDegLat
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return Math.hypot(px - ax, py - ay)
        var t = ((px - ax) * dx + (py - ay) * dy) / len2
        t = t.coerceIn(0.0, 1.0)
        val cx = ax + t * dx; val cy = ay + t * dy
        return Math.hypot(px - cx, py - cy)
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
    }

    fun clearJourneyOnMap() {
        cancelJourneyGeometry()
        _uiState.update {
            it.copy(
                journeySegments = emptyList(),
                journeyMarkers = emptyList(),
                journeyBoardingStopId = null,
                isBuildingRoads = false
            )
        }
    }

    fun toggleTraffic() {
        _uiState.update { it.copy(isTrafficVisible = !it.isTrafficVisible) }
    }

    fun toggleBuses() {
        _uiState.update { it.copy(areBusesVisible = !it.areBusesVisible) }
    }

    fun toggleStops() {
        _uiState.update { it.copy(areStopsVisible = !it.areStopsVisible) }
    }

    fun setMapType(type: MapType) {
        // Спутник/Гибрид раньше автоматически «откатывались» на Схему по таймауту —
        // это и был баг. Теперь просто сохраняем выбранный тип карты.
        viewModelScope.launch {
            settingsRepository.setMapType(type)
        }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    private fun startWeatherUpdates() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            while (true) {
                fetchWeather()
                delay(10 * 60 * 1000)
            }
        }
    }

    private fun stopWeatherUpdates() {
        weatherJob?.cancel()
        weatherJob = null
    }

    fun refreshWeather(force: Boolean = false) {
        viewModelScope.launch {
            fetchWeather(force)
            if (_uiState.value.weatherError == null) {
                _uiState.update { it.copy(showWeatherUpdated = true) }
                delay(2000)
                _uiState.update { it.copy(showWeatherUpdated = false) }
            }
        }
    }

    private suspend fun fetchWeather(force: Boolean = false) {
        _uiState.update { it.copy(isWeatherLoading = true, weatherError = null) }
        try {
            val weather = weatherRepository.getNorilskWeather(force)
            val alerts = evaluateWeatherAlerts(weather)
            _uiState.update {
                it.copy(
                    weather = weather,
                    isWeatherLoading = false,
                    weatherAlerts = alerts,
                    // Если предупреждение исчезло, а потом вернулось — показываем снова.
                    dismissedAlertIds = it.dismissedAlertIds.intersect(alerts.map { a -> a.id }.toSet())
                )
            }
            WeatherAlertNotifier.notifyIfNeeded(getApplication(), alerts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isWeatherLoading = false, 
                    weatherError = "Нет подключения"
                ) 
            }
        }
    }

    /** Пассажир закрыл карточку предупреждения — не показываем до смены погоды. */
    fun dismissWeatherAlert(alertId: String) {
        _uiState.update { it.copy(dismissedAlertIds = it.dismissedAlertIds + alertId) }
    }

    override fun onCleared() {
        super.onCleared()
        stopWeatherUpdates()
        mapLoadingJob?.cancel()
        locationFocusJob?.cancel()
        try { connectivityManager?.unregisterNetworkCallback(networkCallback) } catch (_: RuntimeException) {}
        try { previewTts?.stop(); previewTts?.shutdown() } catch (_: Exception) {}
        previewTts = null
    }

    private companion object {
        const val MAP_LOAD_TIMEOUT_MS = 12_000L
        const val LOCATION_FIX_TIMEOUT_MS = 10_000L
    }

}
