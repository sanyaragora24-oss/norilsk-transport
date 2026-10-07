package com.example.ui.map

import com.example.data.Bus
import com.example.data.Route
import com.example.data.RouteSummary
import com.example.data.Stop
import com.example.data.local.AppTheme
import com.example.data.network.WeatherAlert
import com.example.data.network.WeatherUi
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.MapType

enum class SearchCategory { ROUTES, STOPS }

data class NearbyStop(
    val stop: Stop,
    val distanceMeters: Double,
    val routes: List<RouteSummary>
)

/** Участок построенного маршрута А→Б для отрисовки на карте. */
data class JourneySegmentUi(
    val points: List<Point>,
    val color: Long,
    val isWalk: Boolean,
    val isUserToBoarding: Boolean = false
)

/** Подписанная точка маршрута: Посадка / Пересадка / Выход. Кликабельна как остановка. */
data class JourneyMarkerUi(
    val point: Point,
    val label: String,
    val stopId: String? = null   // чтобы метку можно было открыть как остановку с маршрутами
)

data class MapUiState(
    val routes: List<Route> = emptyList(),
    val selectedRoute: Route? = null,
    val selectedRoutePolyline: List<Point> = emptyList(),
    val isBuildingRoads: Boolean = false,
    val buses: List<Bus> = emptyList(),
    val busDataStatus: String? = null,
    
    // Search
    val searchText: String = "",
    val searchCategory: SearchCategory = SearchCategory.ROUTES,
    val filteredRoutes: List<Route> = emptyList(),
    val filteredStops: List<Stop> = emptyList(),
    
    // Nearby
    val userLocation: Point? = null,
    val nearbyStops: List<NearbyStop> = emptyList(),
    val areNearbyStopsEnabled: Boolean = true,   // переключатель в «Слои и настройки»
    val isNearbyStopsCollapsed: Boolean = false, // закрыто крестиком (до след. нажатия «Где я»)

    // Маршрут А→Б, отрисованный на карте (как в Тюменском)
    val journeySegments: List<JourneySegmentUi> = emptyList(),
    val journeyMarkers: List<JourneyMarkerUi> = emptyList(),
    val journeyBoardingStopId: String? = null,

    // Favorites & arrivals
    val favoriteStopIds: Set<String> = emptySet(),
    val favoriteRouteIds: Set<String> = emptySet(),
    val favoriteGroups: List<com.example.data.local.FavoriteGroup> = emptyList(),
    val savedJourneys: List<com.example.data.local.SavedJourney> = emptyList(),
    val arrivalsAtSelectedStop: Map<String, Int?> = emptyMap(),
    /**
     * Маршруты, у которых ближайший подходящий автобус — низкопольный.
     * Для пассажира с коляской или тростью это решает, ехать сейчас или ждать.
     */
    val accessibleArrivalsAtSelectedStop: Set<String> = emptySet(),

    // Расписание (офлайн, официальные данные НПОПАТ)
    val scheduleByRouteId: Map<String, com.example.data.RouteSchedule> = emptyMap(),
    val scheduleDataDate: String = "",
    
    // Layer Visibility
    val isTrafficVisible: Boolean = false,
    val areBusesVisible: Boolean = true,
    val areStopsVisible: Boolean = true,

    // Планировщик поездки А→Б
    val plannerFrom: Stop? = null,
    val plannerTo: Stop? = null,
    val plannerFromUsesCurrentLocation: Boolean = false,
    val plannerJourney: com.example.data.RoutePlanner.Journey? = null,
    val plannerVariants: List<com.example.data.RoutePlanner.Journey> = emptyList(),
    val selectedVariant: Int = 0,
    val isPlanning: Boolean = false,
    val plannerError: String? = null,

    // Оповещение «скоро ваша остановка» (будильник по GPS)
    val alarmStopId: String? = null,   // остановка, на которую сейчас включён будильник (null = выключен)
    // Голос включён по умолчанию: в пургу и полярную ночь пассажир часто не
    // смотрит на экран, а для незрячих это единственный способ понять, что пора
    // выходить. Выключить можно одним переключателем.
    val alarmVoice: Boolean = true,    // true = сигнал + голос (TTS), false = только сигнал
    
    // Interaction
    val highlightedStopId: String? = null,
    
    // Map Settings
    val mapType: MapType = MapType.MAP,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val cameraZoom: Float = 11.0f,
    val isMapLoaded: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    val mapLoadTimedOut: Boolean = false,
    val isLocatingUser: Boolean = false,
    val locationFixTimedOut: Boolean = false,
    
    // Weather
    val weather: WeatherUi? = null,
    val isWeatherLoading: Boolean = false,
    val weatherError: String? = null,
    val showWeatherUpdated: Boolean = false,
    /** Действующие предупреждения о непогоде, самое важное — первым. */
    val weatherAlerts: List<WeatherAlert> = emptyList(),
    /** Предупреждения, которые пассажир закрыл вручную в этой сессии. */
    val dismissedAlertIds: Set<String> = emptySet(),
    /** Пользователь далеко от Норильска — «ближайшие остановки» показывать нельзя. */
    val isOutsideServiceArea: Boolean = false,
    
    // Bottom Sheet
    val selectedStop: Stop? = null,
    val routesAtSelectedStop: List<RouteSummary> = emptyList()
)
