package com.example.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.lang.ref.WeakReference
import com.example.R
import com.example.BuildConfig
import com.example.data.Route
import com.example.data.Stop
import com.example.data.matchesRouteForDisplay
import com.example.data.matchesTransitSearch
import com.example.data.local.AppTheme
import com.example.data.network.WeatherAlertLevel
import com.example.ui.components.*
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.*
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun EmptySearchResult(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            if (query.isBlank()) "Список пуст" else "Ничего не найдено",
            style = MaterialTheme.typography.titleMedium
        )
        if (query.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Проверьте номер или название без сокращений",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    onNavigateToSupport: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isDarkUi = when (uiState.appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    var showRoutesSheet by rememberSaveable { mutableStateOf(false) }
    var showLayersSheet by rememberSaveable { mutableStateOf(false) }
    var showMenuSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showFavoritesSheet by rememberSaveable { mutableStateOf(false) }
    var showPlannerSheet by rememberSaveable { mutableStateOf(false) }
    var showWeatherOverlay by rememberSaveable { mutableStateOf(false) }
    
    var routeDetails by remember { mutableStateOf<Route?>(null) }

    val routesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val layersSheetState = rememberModalBottomSheetState()
    val menuSheetState = rememberModalBottomSheetState()

    val routesListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val dateTimeText by produceState(initialValue = "") {
        val formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
        val norilskZone = ZoneId.of("Asia/Krasnoyarsk")
        while (true) {
            value = LocalDateTime.now(norilskZone).format(formatter)
            delay(60_000)
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingAlarmStop by remember { mutableStateOf<Stop?>(null) }

    // Надёжная геолокация через FusedLocationProvider → подсказки «Ближайшие остановки»
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    androidx.lifecycle.compose.LifecycleStartEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            onStopOrDispose { }
        } else {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        viewModel.onUserLocationChanged(Point(loc.latitude, loc.longitude))
                    }
                }
            }
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    loc?.let { viewModel.onUserLocationChanged(Point(it.latitude, it.longitude)) }
                }
                fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                Log.e("MapScreen", "Location permission error", e)
            }
            onStopOrDispose { fusedClient.removeLocationUpdates(callback) }
        }
    }

    // Permissions logic
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            hasLocationPermission = true
            viewModel.focusUserLocation()
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Нужно разрешение геолокации",
                    actionLabel = "Настройки",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    // Отдельный запрос под штормовые оповещения: пассажир, который никогда не
    // пользовался будильником, иначе не получит предупреждение о пурге и не
    // поймёт почему.
    val weatherNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Предупреждения о непогоде включены")
            }
        } else {
            openNotificationSettings(context)
        }
    }

    val alarmPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // The 350 m / arrival alarm needs precise fixes; coarse-only location can miss both thresholds.
        val preciseLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (preciseLocationGranted && notificationGranted) {
            hasLocationPermission = true
            pendingAlarmStop?.let(viewModel::armStopAlarm)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Для будильника нужны точная геолокация и уведомления",
                    duration = SnackbarDuration.Long
                )
            }
        }
        pendingAlarmStop = null
    }

    fun armAlarmWithPermissions(stop: Stop) {
        val hasPreciseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (hasPreciseLocation && hasNotifications) {
            viewModel.armStopAlarm(stop)
            return
        }

        pendingAlarmStop = stop
        val requested = mutableListOf<String>()
        if (!hasPreciseLocation) {
            requested += Manifest.permission.ACCESS_FINE_LOCATION
            requested += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (!hasNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requested += Manifest.permission.POST_NOTIFICATIONS
        }
        alarmPermissionLauncher.launch(requested.toTypedArray())
    }

    fun handleMyLocationClick() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "GPS выключен",
                    actionLabel = "Включить",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            return
        }

        if (hasLocationPermission) {
            viewModel.focusUserLocation()
            return
        }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(uiState.weatherError) {
        uiState.weatherError?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Повторить",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.refreshWeather(force = true)
            }
        }
    }

    LaunchedEffect(uiState.locationFixTimedOut) {
        if (uiState.locationFixTimedOut) {
            snackbarHostState.showSnackbar(
                message = "Не удалось определить местоположение. Проверьте GPS и попробуйте снова.",
                duration = SnackbarDuration.Long
            )
            viewModel.consumeLocationFixTimeout()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dateTimeText,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Black
                        )
                        val weather = uiState.weather
                        if (weather != null) {
                            Spacer(Modifier.width(8.dp))
                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                            // Нажатие на температуру/ветер открывает окно с погодой + «Дорожные условия»
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showWeatherOverlay = true }
                            ) {
                                Text(
                                    text = "${weather.temp.toInt()}°C",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${weather.windSpeed} м/с",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            IconButton(onClick = { viewModel.refreshWeather(force = true) }) {
                                if (uiState.showWeatherUpdated) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Обновить", modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            // Первый запрос не ответил (нет сети или оба источника недоступны).
                            // Не исчезаем: показываем безопасное состояние и оставляем повтор.
                            Spacer(Modifier.width(8.dp))
                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                            Text(
                                text = if (uiState.isWeatherLoading) "Погода…" else "Погода недоступна",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.clickable { showWeatherOverlay = true }
                            )
                            IconButton(onClick = { viewModel.refreshWeather(force = true) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Повторить загрузку погоды", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showMenuSheet = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Меню")
                    }
                },
                actions = {
                    IconButton(onClick = { showFavoritesSheet = true }) {
                        Icon(Icons.Default.Star, contentDescription = "Избранное")
                    }
                    IconButton(onClick = { showLayersSheet = true }) {
                        Icon(Icons.Default.Layers, contentDescription = "Слои")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            YandexMapView(
                uiState = uiState,
                onRouteClick = { viewModel.selectRoute(it) },
                onStopClick = { viewModel.selectStopAndFocus(it) },
                onUserLocationChanged = { viewModel.onUserLocationChanged(it) },
                onMapLoaded = { viewModel.onMapLoaded() },
                onCameraMoved = { viewModel.onCameraMoved(it) },
                isSheetOpen = showRoutesSheet,
                nightMode = isDarkUi,
                commands = viewModel.mapCommands
            )

            when (MapInteractionPolicy.surfaceStatus(
                isLoaded = uiState.isMapLoaded,
                networkAvailable = uiState.isNetworkAvailable,
                loadTimedOut = uiState.mapLoadTimedOut
            )) {
                MapInteractionPolicy.SurfaceStatus.OFFLINE,
                MapInteractionPolicy.SurfaceStatus.LOAD_FAILED -> {
                    val offline = !uiState.isNetworkAvailable
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (offline) "Карта недоступна без интернета" else "Не удалось загрузить карту",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Маршруты и расписания остаются доступны в меню.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                MapInteractionPolicy.SurfaceStatus.LOADING,
                MapInteractionPolicy.SurfaceStatus.READY -> Unit
            }

            // Штормовое предупреждение — над всем остальным содержимым карты:
            // пассажир должен увидеть его до того, как начнёт планировать поездку.
            uiState.weatherAlerts
                .firstOrNull {
                    // ADVISORY баннером не показываем: в Норильске ветер 8 м/с и
                    // мороз −25 °C — рядовая погода, ради неё перекрывать карту
                    // нельзя. Такие вещи видны в карточке погоды.
                    it.level != WeatherAlertLevel.ADVISORY && it.id !in uiState.dismissedAlertIds
                }
                ?.let { alert ->
                    WeatherAlertBanner(
                        alert = alert,
                        onDismiss = { viewModel.dismissWeatherAlert(alert.id) },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

            uiState.busDataStatus?.let { status ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = if (uiState.weatherAlerts.any {
                                    it.level != WeatherAlertLevel.ADVISORY && it.id !in uiState.dismissedAlertIds
                                }
                            ) 96.dp else 8.dp
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Nearby Stops Overlay Panel
            AnimatedVisibility(
                visible = uiState.areNearbyStopsEnabled && !uiState.isNearbyStopsCollapsed && uiState.nearbyStops.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 80.dp, bottom = 32.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Ближайшие остановки",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            // Кнопка закрыть шторку
                            IconButton(
                                onClick = { viewModel.collapseNearbyStops() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            "Нажмите остановку — расписание и прибытие автобусов",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            uiState.nearbyStops.forEach { nearby ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectStopAndFocus(nearby.stop) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(nearby.stop.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text(formatDistance(nearby.distanceMeters), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(nearby.routes.take(5)) { routeSummary ->
                                            SuggestionChip(
                                                onClick = { 
                                                    val fullRoute = uiState.routes.find { it.id == routeSummary.id }
                                                    viewModel.selectRoute(fullRoute)
                                                },
                                                label = { Text(routeSummary.number) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = Color(routeSummary.color).copy(alpha = 0.1f),
                                                    labelColor = Color(routeSummary.color)
                                                ),
                                                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = Color(routeSummary.color).copy(alpha = 0.3f))
                                            )
                                        }
                                    }
                                }
                                if (nearby != uiState.nearbyStops.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Пользователь далеко от города: честное объяснение вместо списка
            // остановок, до которых тысячи километров.
            AnimatedVisibility(
                visible = uiState.areNearbyStopsEnabled &&
                    !uiState.isNearbyStopsCollapsed &&
                    uiState.isOutsideServiceArea,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 80.dp, bottom = 32.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Вы далеко от Норильска",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.collapseNearbyStops() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Приложение показывает транспорт Норильска. Маршруты и " +
                                "расписания доступны, но ближайшие к вам остановки показать не получится.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom Right controls — прижаты к правому краю
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Компактная кнопка построения маршрута А→Б (иконка вместо широкой кнопки)
                FloatingActionButton(
                    onClick = { showPlannerSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = "Маршрут А→Б")
                }

                FloatingActionButton(
                    onClick = { handleMyLocationClick() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    if (uiState.isLocatingUser) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = "Где я")
                    }
                }

                // Зум +/- картой
                FloatingActionButton(
                    onClick = { viewModel.zoomIn() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Приблизить")
                }

                FloatingActionButton(
                    onClick = { viewModel.zoomOut() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Отдалить")
                }

                FloatingActionButton(
                    onClick = { viewModel.toggleTraffic() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (uiState.isTrafficVisible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Traffic, contentDescription = "Пробки")
                }
                
                FloatingActionButton(
                    onClick = { showRoutesSheet = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Маршруты")
                }
            }
        }

        // Окно погоды + карточка «Дорожные условия» (по тапу на температуру в шапке)
        WeatherOverlay(
            visible = showWeatherOverlay,
            isLoading = uiState.isWeatherLoading,
            weather = uiState.weather,
            onDismiss = { showWeatherOverlay = false },
            errorMessage = uiState.weatherError,
            onRetry = { viewModel.refreshWeather(force = true) }
        )

        // Stop Details Sheet
        uiState.selectedStop?.let { stop ->
            StopBottomSheet(
                stop = stop,
                routes = uiState.routesAtSelectedStop,
                isFavorite = uiState.favoriteStopIds.contains(stop.id),
                onToggleFavorite = { viewModel.toggleFavoriteStop(stop.id) },
                arrivals = uiState.arrivalsAtSelectedStop,
                accessibleRouteIds = uiState.accessibleArrivalsAtSelectedStop,
                scheduleByRouteId = uiState.scheduleByRouteId,
                dataDate = uiState.scheduleDataDate,
                alarmStopId = uiState.alarmStopId,
                alarmVoice = uiState.alarmVoice,
                onSetAlarmVoice = { viewModel.setAlarmVoice(it) },
                onArmAlarm = { armAlarmWithPermissions(stop) },
                onCancelAlarm = { viewModel.cancelStopAlarm() },
                onTestAlarm = { viewModel.testAlarm(stop) },
                onRouteClick = { routeId ->
                    val route = uiState.routes.find { it.id == routeId }
                    viewModel.selectRoute(route)
                    viewModel.selectStop(null)
                },
                onDismiss = { viewModel.selectStop(null) }
            )
        }

        // Favorites Sheet (с группами «На работу», «На дачу» …)
        if (showFavoritesSheet) {
            FavoritesSheet(
                routes = uiState.routes,
                favoriteRouteIds = uiState.favoriteRouteIds,
                favoriteStopIds = uiState.favoriteStopIds,
                groups = uiState.favoriteGroups,
                onDismiss = { showFavoritesSheet = false },
                onToggleFavoriteRoute = { viewModel.toggleFavoriteRoute(it) },
                onToggleFavoriteStop = { viewModel.toggleFavoriteStop(it) },
                onSelectRoute = { route ->
                    viewModel.selectRoute(route)
                    showFavoritesSheet = false
                },
                onSelectStop = { stop ->
                    viewModel.selectStopAndFocus(stop)
                    showFavoritesSheet = false
                },
                onCreateGroup = { viewModel.createFavoriteGroup(it) },
                onRenameGroup = { id, name -> viewModel.renameFavoriteGroup(id, name) },
                onDeleteGroup = { viewModel.deleteFavoriteGroup(it) },
                onToggleRouteInGroup = { gid, rid -> viewModel.toggleRouteInGroup(gid, rid) },
                onToggleStopInGroup = { gid, sid -> viewModel.toggleStopInGroup(gid, sid) },
                savedJourneys = uiState.savedJourneys,
                onOpenSavedJourney = { sj ->
                    viewModel.applySavedJourney(sj)
                    showFavoritesSheet = false
                    showPlannerSheet = true
                },
                onDeleteSavedJourney = { viewModel.removeSavedJourney(it) }
            )
        }

        // Planner Sheet (А→Б с пересадками)
        if (showPlannerSheet) {
            val allStops = remember(uiState.routes) {
                uiState.routes.flatMap { it.stops }.distinctBy { it.name }.sortedBy { it.name }
            }
            PlannerSheet(
                from = uiState.plannerFrom,
                to = uiState.plannerTo,
                canUseCurrentLocation = uiState.userLocation != null,
                fromUsesCurrentLocation = uiState.plannerFromUsesCurrentLocation,
                journey = uiState.plannerJourney,
                variants = uiState.plannerVariants,
                selectedVariant = uiState.selectedVariant,
                onSelectVariant = { viewModel.selectVariant(it) },
                isPlanning = uiState.isPlanning,
                error = uiState.plannerError,
                allStops = allStops,
                onSetFrom = { viewModel.setPlannerFrom(it) },
                onUseCurrentLocation = { viewModel.setPlannerFromCurrentLocation() },
                onSetTo = { viewModel.setPlannerTo(it) },
                onSwap = { viewModel.swapPlannerEndpoints() },
                onClear = { viewModel.clearPlanner() },
                onBuild = { viewModel.buildJourney() },
                onShowRouteOnMap = { routeId ->
                    viewModel.showJourneyRouteOnMap(routeId)
                    showPlannerSheet = false
                },
                onShowJourneyOnMap = {
                    viewModel.showJourneyOnMap()
                    showPlannerSheet = false
                },
                savedJourneys = uiState.savedJourneys,
                isCurrentSaved = uiState.savedJourneys.any {
                    it.fromStopId == uiState.plannerFrom?.id && it.toStopId == uiState.plannerTo?.id
                },
                onSaveJourney = { viewModel.saveCurrentJourney() },
                onOpenSaved = { viewModel.applySavedJourney(it) },
                onDeleteSaved = { viewModel.removeSavedJourney(it) },
                onDismiss = { showPlannerSheet = false }
            )
        }

        // Routes/Search Sheet
        if (showRoutesSheet) {
            ModalBottomSheet(
                sheetState = routesSheetState,
                onDismissRequest = { 
                    showRoutesSheet = false
                    routeDetails = null 
                },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    if (routeDetails == null) {
                        // Search Bar
                        OutlinedTextField(
                            value = uiState.searchText,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    if (uiState.searchCategory == SearchCategory.ROUTES) {
                                        "Номер или направление маршрута"
                                    } else {
                                        "Название остановки"
                                    }
                                )
                            },
                            leadingIcon = { 
                                if (uiState.searchText.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                                    }
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                }
                            },
                            trailingIcon = {
                                if (uiState.searchText.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // Category Selector
                        TabRow(
                            selectedTabIndex = uiState.searchCategory.ordinal,
                            containerColor = Color.Transparent,
                            divider = {}
                        ) {
                            SearchCategory.entries.forEach { category ->
                                Tab(
                                    selected = uiState.searchCategory == category,
                                    onClick = { viewModel.setSearchCategory(category) },
                                    text = { Text(if (category == SearchCategory.ROUTES) "Маршруты" else "Остановки") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            state = routesListState,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            if (uiState.searchCategory == SearchCategory.ROUTES) {
                                if (uiState.filteredRoutes.isEmpty()) {
                                    item { EmptySearchResult(uiState.searchText) }
                                }
                                items(uiState.filteredRoutes, key = { it.id }) { route ->
                                    RouteCard(
                                        route = route,
                                        onBadgeClick = { 
                                            viewModel.selectRoute(route)
                                            scope.launch { routesSheetState.partialExpand() }
                                        },
                                        onTextClick = { routeDetails = route }
                                    )
                                }
                            } else {
                                if (uiState.filteredStops.isEmpty()) {
                                    item { EmptySearchResult(uiState.searchText) }
                                }
                                items(uiState.filteredStops, key = { it.id }) { stop ->
                                    StopCard(
                                        stop = stop,
                                        routeNumbers = viewModel.routeNumbersForStop(stop.id),
                                        onClick = { 
                                            viewModel.selectStopAndFocus(stop)
                                            scope.launch { routesSheetState.partialExpand() }
                                        }
                                    )
                                }
                            }
                        }
                        
                        Button(
                            onClick = { showRoutesSheet = false },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Закрыть")
                        }
                    } else {
                        RouteDetailsView(
                            route = routeDetails!!,
                            isBuildingRoads = uiState.isBuildingRoads,
                            isFavorite = uiState.favoriteRouteIds.contains(routeDetails!!.id),
                            onToggleFavorite = { viewModel.toggleFavoriteRoute(routeDetails!!.id) },
                            schedule = uiState.scheduleByRouteId[routeDetails!!.id],
                            dataDate = uiState.scheduleDataDate,
                            onBack = { routeDetails = null },
                            onShowOnMap = {
                                viewModel.selectRoute(routeDetails)
                                scope.launch { routesSheetState.partialExpand() }
                            },
                            onStopClick = { stop ->
                                viewModel.selectStopAndFocus(stop)
                                scope.launch { routesSheetState.partialExpand() }
                            },
                            onClose = { showRoutesSheet = false }
                        )
                    }
                }
            }
        }

        // Layers Sheet
        if (showLayersSheet) {
            ModalBottomSheet(
                sheetState = layersSheetState,
                onDismissRequest = { showLayersSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text("Слои и настройки", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LayerToggle("Пробки", uiState.isTrafficVisible) { viewModel.toggleTraffic() }
                    LayerToggle("Автобусы", uiState.areBusesVisible) { viewModel.toggleBuses() }
                    LayerToggle("Остановки", uiState.areStopsVisible) { viewModel.toggleStops() }
                    LayerToggle("Ближайшие остановки", uiState.areNearbyStopsEnabled) { viewModel.toggleNearbyStops() }
                }
            }
        }

        // Menu Sheet
        if (showMenuSheet) {
            ModalBottomSheet(
                sheetState = menuSheetState,
                onDismissRequest = { showMenuSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text("Норильский Транспорт", style = MaterialTheme.typography.titleLarge)
                    Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Тема оформления", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeButton("Системная", AppTheme.SYSTEM, uiState.appTheme) { viewModel.setAppTheme(it) }
                        ThemeButton("Светлая", AppTheme.LIGHT, uiState.appTheme) { viewModel.setAppTheme(it) }
                        ThemeButton("Тёмная", AppTheme.DARK, uiState.appTheme) { viewModel.setAppTheme(it) }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Управление штормовыми оповещениями живёт в системных
                    // настройках: там пассажир может отдельно оставить
                    // предупреждения о пурге и отключить бытовые.
                    ListItem(
                        headlineContent = { Text("Оповещения о непогоде") },
                        supportingContent = {
                            Text("Пурга, мороз, ограничение движения между районами")
                        },
                        trailingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showMenuSheet = false
                            val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            if (needsRequest) {
                                weatherNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openNotificationSettings(context)
                            }
                        }
                    )

                    ListItem(
                        headlineContent = { Text("О приложении") },
                        supportingContent = { Text("Карта общественного транспорта города Норильск") },
                        trailingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                        modifier = Modifier.clickable { showAboutDialog = true }
                    )
                    
                    ListItem(
                        headlineContent = { Text("Поддержка") },
                        supportingContent = { Text("Написать разработчикам / Сообщить об ошибке") },
                        modifier = Modifier.clickable { 
                            showMenuSheet = false
                            onNavigateToSupport() 
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.rotate(180f)) }
                    )
                    
                }
            }
        }

        // Диалог «О приложении»
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) { Text("Закрыть") }
                },
                icon = { Icon(Icons.Default.DirectionsBus, contentDescription = null) },
                title = { Text("Норильский Транспорт") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Карта и планировщик общественного транспорта города Норильск.", style = MaterialTheme.typography.bodyMedium)
                        Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider()
                        Text("• Маршруты и остановки — офлайн-данные НПОПАТ", style = MaterialTheme.typography.bodySmall)
                        Text("• Карты — Yandex MapKit", style = MaterialTheme.typography.bodySmall)
                        Text("• Погода и дорожные условия — Open-Meteo", style = MaterialTheme.typography.bodySmall)
                        Text("Приложение неофициальное, создано для удобства пассажиров.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

private fun Modifier.rotate(degrees: Float): Modifier = this.then(
    Modifier.graphicsLayer(rotationZ = degrees)
)

@Composable
fun ThemeButton(label: String, theme: AppTheme, current: AppTheme, onClick: (AppTheme) -> Unit) {
    FilterChip(
        selected = current == theme,
        onClick = { onClick(theme) },
        label = { Text(label) }
    )
}

@Composable
fun RouteDetailsView(
    route: Route,
    isBuildingRoads: Boolean,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    schedule: com.example.data.RouteSchedule? = null,
    dataDate: String = "",
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onStopClick: (Stop) -> Unit,
    onClose: () -> Unit
) {
    var showSchedule by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(text = "Маршрут №${route.number}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Избранное",
                    tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClose) { Text("Закрыть") }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "${route.origin} ↔ ${route.destination}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (isBuildingRoads) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Button(
                onClick = onShowOnMap,
                modifier = Modifier.weight(1f)
            ) {
                Text("На карте")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { showSchedule = !showSchedule },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showSchedule) "Скрыть" else "Расписание")
            }
        }

        if (showSchedule) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    com.example.ui.components.ScheduleView(schedule = schedule, dataDate = dataDate)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Остановки (${route.stops.size}):",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall
        )
        
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(route.stops) { stop ->
                ListItem(
                    headlineContent = { Text(stop.name) },
                    modifier = Modifier.clickable { onStopClick(stop) },
                    trailingContent = {
                        ElevatedAssistChip(
                            onClick = { onStopClick(stop) },
                            label = { Text("На карту") }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun LayerToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun YandexMapView(
    uiState: MapUiState,
    onRouteClick: (Route) -> Unit,
    onStopClick: (Stop) -> Unit,
    onUserLocationChanged: (Point) -> Unit,
    onMapLoaded: () -> Unit,
    onCameraMoved: (Float) -> Unit,
    isSheetOpen: Boolean,
    nightMode: Boolean,
    commands: kotlinx.coroutines.flow.SharedFlow<MapCommand>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // актуальная ссылка на состояние для слушателя тапов (без устаревшего замыкания)
    val currentUiState by rememberUpdatedState(uiState)
    val currentOnStopClick by rememberUpdatedState(onStopClick)
    val currentOnMapLoaded by rememberUpdatedState(onMapLoaded)
    val currentOnCameraMoved by rememberUpdatedState(onCameraMoved)
    val currentOnUserLocationChanged by rememberUpdatedState(onUserLocationChanged)

    val mapView = remember {
        MapView(context).apply {
            mapWindow.map.move(
                CameraPosition(Point(69.3558, 88.1893), 11.0f, 0.0f, 0.0f)
            )
        }
    }
    
    val trafficLayer = remember {
        MapKitFactory.getInstance().createTrafficLayer(mapView.mapWindow).apply {
            isTrafficVisible = uiState.isTrafficVisible
        }
    }

    val locationLayer = remember {
        MapKitFactory.getInstance().createUserLocationLayer(mapView.mapWindow).apply {
            isVisible = true
        }
    }

    val routeCollection = remember { mapView.mapWindow.map.mapObjects.addCollection() }
    val journeyCollection = remember { mapView.mapWindow.map.mapObjects.addCollection() }
    val stopsCollection = remember { mapView.mapWindow.map.mapObjects.addCollection() }
    val busesCollection = remember { mapView.mapWindow.map.mapObjects.addCollection() }

    val stopIcon = remember { imageProviderFromVector(context, R.drawable.ic_stop_marker) }
    val stopSelectedIcon = remember { imageProviderFromVector(context, R.drawable.ic_stop_selected_marker) }
    val busIcon = remember { imageProviderFromVector(context, R.drawable.ic_bus_marker) }

    // Persistent tap listener for stops (ищем остановку среди ВСЕХ маршрутов,
    // используя актуальное состояние — раньше тут было устаревшее замыкание и тап не срабатывал)
    val stopTapListener = remember {
        MapObjectTapListener { mapObject, _ ->
            val stopId = mapObject.userData as? String
            if (stopId != null) {
                val stop = currentUiState.routes
                    .asSequence()
                    .flatMap { it.stops.asSequence() }
                    .firstOrNull { it.id == stopId }
                if (stop != null) {
                    currentOnStopClick(stop)
                }
            }
            true
        }
    }
    val stopTapListenerRef = remember(stopTapListener) { WeakReference(stopTapListener) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                }
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
        }
    }

    LaunchedEffect(uiState.isTrafficVisible) { trafficLayer.isTrafficVisible = uiState.isTrafficVisible }
    // Ночной режим карты вслед за тёмной темой приложения
    LaunchedEffect(nightMode) { mapView.mapWindow.map.setNightModeEnabled(nightMode) }
    
    // Handle commands
    LaunchedEffect(commands) {
        commands.collect { command ->
            when (command) {
                is MapCommand.FocusStop -> {
                    val targetPoint = Point(command.point.latitude - 0.003, command.point.longitude)
                    mapView.mapWindow.map.move(
                        CameraPosition(targetPoint, command.zoom, 0f, 0f),
                        com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, 0.8f),
                        null
                    )
                }
                is MapCommand.FocusPoints -> {
                    if (command.points.isNotEmpty()) {
                        val geometry = Geometry.fromPolyline(Polyline(command.points))
                        mapView.mapWindow.map.move(
                            mapView.mapWindow.map.cameraPosition(geometry),
                            com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, 0.8f),
                            null
                        )
                    }
                }
                is MapCommand.FocusUserLocation -> {
                    mapView.mapWindow.map.move(
                        CameraPosition(command.point, 15f, 0f, 0f),
                        com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, 0.8f),
                        null
                    )
                }
                MapCommand.ZoomIn -> {
                    val cur = mapView.mapWindow.map.cameraPosition
                    mapView.mapWindow.map.move(
                        CameraPosition(cur.target, cur.zoom + 1f, cur.azimuth, cur.tilt),
                        com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, 0.3f),
                        null
                    )
                }
                MapCommand.ZoomOut -> {
                    val cur = mapView.mapWindow.map.cameraPosition
                    mapView.mapWindow.map.move(
                        CameraPosition(cur.target, cur.zoom - 1f, cur.azimuth, cur.tilt),
                        com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, 0.3f),
                        null
                    )
                }
            }
        }
    }

    // MapKit holds only weak references: keep listeners alive in the composition.
    val cameraListener = remember(mapView) {
        CameraListener { _, pos, _, _ ->
            currentOnCameraMoved(pos.zoom)
        }
    }
    val mapLoadedListener = remember(mapView) {
        MapLoadedListener {
            currentOnMapLoaded()
        }
    }
    val userLocationListener = remember(mapView, locationLayer) {
        object : UserLocationObjectListener {
            override fun onObjectAdded(view: UserLocationView) {}
            override fun onObjectRemoved(view: UserLocationView) {}
            override fun onObjectUpdated(view: UserLocationView, event: com.yandex.mapkit.layers.ObjectEvent) {
                locationLayer.cameraPosition()?.let { pos ->
                    currentOnUserLocationChanged(pos.target)
                }
            }
        }
    }

    DisposableEffect(mapView, cameraListener, mapLoadedListener, userLocationListener) {
        val cameraListenerRef = WeakReference(cameraListener)
        mapView.mapWindow.map.addCameraListener(cameraListenerRef)
        mapView.mapWindow.map.setMapLoadedListener(WeakReference(mapLoadedListener))
        locationLayer.setObjectListener(WeakReference(userLocationListener))
        
        onDispose { 
            mapView.mapWindow.map.removeCameraListener(cameraListenerRef)
            mapView.mapWindow.map.setMapLoadedListener(null)
            locationLayer.setObjectListener(null)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            if (view.mapWindow.map.mapType != uiState.mapType) {
                view.mapWindow.map.mapType = uiState.mapType
            }
        }
    )

    // Selective updates of map objects
    LaunchedEffect(uiState.selectedRoutePolyline, uiState.selectedRoute?.color) {
        routeCollection.clear()
        if (uiState.selectedRoutePolyline.isNotEmpty() && uiState.selectedRoute != null) {
            val geometry = Polyline(uiState.selectedRoutePolyline)
            routeCollection.addPolyline(geometry).apply {
                setStrokeColor(0xFF000000.toInt())
                strokeWidth = 8f
                zIndex = 1f
            }
            routeCollection.addPolyline(geometry).apply {
                setStrokeColor(uiState.selectedRoute!!.color.toInt())
                strokeWidth = 4f
                zIndex = 2f
            }
        }
    }

    // Маршрут А→Б на карте: автобусы цветом маршрута, пешком — серым,
    // точки Посадка / Пересадка / Выход подписаны прямо на карте.
    LaunchedEffect(uiState.journeySegments, uiState.journeyMarkers) {
        journeyCollection.clear()
        uiState.journeySegments.forEach { seg ->
            if (seg.points.size >= 2) {
                val pl = Polyline(seg.points)
                if (seg.isWalk) {
                    // пешком — серая ПУНКТИРНАЯ линия (в т.ч. от геолокации до остановки)
                    journeyCollection.addPolyline(pl).apply {
                        setStrokeColor(seg.color.toInt())   // серый
                        strokeWidth = 4f
                        zIndex = 5f
                        try {
                            dashLength = 8f
                            gapLength = 6f
                        } catch (_: Throwable) { /* старый MapKit без пунктира — оставим сплошную */ }
                    }
                } else {
                    // тёмная окантовка + цвет маршрута
                    journeyCollection.addPolyline(pl).apply {
                        setStrokeColor(0xFF000000.toInt())
                        strokeWidth = 9f
                        zIndex = 5f
                    }
                    journeyCollection.addPolyline(pl).apply {
                        setStrokeColor(seg.color.toInt())
                        strokeWidth = 5f
                        zIndex = 6f
                    }
                }
            }
        }
        uiState.journeyMarkers.forEach { m ->
            journeyCollection.addPlacemark(m.point).apply {
                setIcon(stopSelectedIcon)
                zIndex = 12f
                setText(m.label, com.yandex.mapkit.map.TextStyle().apply {
                    size = 11f
                    offset = 6f
                    placement = TextStyle.Placement.TOP
                })
                // метка кликабельна как обычная остановка (откроет маршруты/расписание)
                if (m.stopId != null) {
                    userData = m.stopId
                    addTapListener(stopTapListenerRef)
                }
            }
        }
    }

    LaunchedEffect(uiState.selectedRoute?.stops, uiState.areStopsVisible, uiState.highlightedStopId) {
        stopsCollection.clear()
        if (uiState.areStopsVisible && uiState.selectedRoute != null) {
            uiState.selectedRoute!!.stops.forEach { stop ->
                val isHighlighted = stop.id == uiState.highlightedStopId
                val pm = stopsCollection.addPlacemark(stop.location)
                pm.setIcon(if (isHighlighted) stopSelectedIcon else stopIcon)
                pm.zIndex = if (isHighlighted) 10f else 3f
                pm.userData = stop.id
                pm.addTapListener(stopTapListenerRef)
                
                // Названия остановок показываем всегда для выбранного маршрута
                pm.setText(stop.name, com.yandex.mapkit.map.TextStyle().apply {
                    size = if (isHighlighted) 12f else 9f
                    offset = 5f
                    placement = TextStyle.Placement.BOTTOM
                })
            }
        }
    }

    LaunchedEffect(uiState.buses, uiState.areBusesVisible, uiState.selectedRoute?.id) {
        busesCollection.clear()
        if (uiState.areBusesVisible) {
            uiState.buses.forEach { bus ->
                if (uiState.selectedRoute == null || bus.matchesRouteForDisplay(uiState.selectedRoute)) {
                    busesCollection.addPlacemark(bus.location).apply {
                        setIcon(busIcon)
                        zIndex = 4f
                    }
                }
            }
        }
    }

    // Camera move effect when route is selected (initial focus)
    LaunchedEffect(uiState.selectedRoute?.id) {
        uiState.selectedRoute?.let { route ->
            if (route.polyline.isNotEmpty()) {
                val geometry = Geometry.fromPolyline(Polyline(route.polyline))
                var cameraPos = mapView.mapWindow.map.cameraPosition(geometry)
                
                if (isSheetOpen) {
                    cameraPos = CameraPosition(
                        Point(cameraPos.target.latitude - 0.01, cameraPos.target.longitude),
                        cameraPos.zoom - 0.5f,
                        cameraPos.azimuth,
                        cameraPos.tilt
                    )
                }

                mapView.mapWindow.map.move(
                    cameraPos,
                    com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, 0.8f),
                    null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerSheet(
    from: Stop?,
    to: Stop?,
    canUseCurrentLocation: Boolean,
    fromUsesCurrentLocation: Boolean,
    journey: com.example.data.RoutePlanner.Journey?,
    variants: List<com.example.data.RoutePlanner.Journey> = emptyList(),
    selectedVariant: Int = 0,
    onSelectVariant: (Int) -> Unit = {},
    isPlanning: Boolean,
    error: String?,
    allStops: List<Stop>,
    onSetFrom: (Stop) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSetTo: (Stop) -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit = {},
    onBuild: () -> Unit,
    onShowRouteOnMap: (String) -> Unit,
    onShowJourneyOnMap: () -> Unit,
    savedJourneys: List<com.example.data.local.SavedJourney> = emptyList(),
    isCurrentSaved: Boolean = false,
    onSaveJourney: () -> Unit = {},
    onOpenSaved: (com.example.data.local.SavedJourney) -> Unit = {},
    onDeleteSaved: (com.example.data.local.SavedJourney) -> Unit = {},
    onDismiss: () -> Unit
) {
    // 0 = ничего не редактируем, 1 = Откуда, 2 = Куда
    var editing by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    // Показываем ВСЕ остановки (без обрезки до 30/40)
    val filtered = remember(query, allStops) {
        if (query.isBlank()) allStops
        else allStops.filter { matchesTransitSearch(query, it.name) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Построить маршрут", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                // «Очистить» — сбрасывает Откуда/Куда, построенный маршрут и линию на карте,
                // чтобы можно было строить новый маршрут с нуля.
                if (from != null || to != null || journey != null) {
                    TextButton(onClick = {
                        onClear()
                        editing = 0
                        query = ""
                    }) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Очистить")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Поля Откуда / Куда
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EndpointField(
                        "Откуда",
                        if (fromUsesCurrentLocation && from != null) "Моё местоположение · ${from.name}" else from?.name
                    ) { editing = 1; query = "" }
                    EndpointField("Куда", to?.name) { editing = 2; query = "" }
                }
                IconButton(onClick = onSwap) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Поменять местами")
                }
            }

            Spacer(Modifier.height(12.dp))

            if (editing != 0) {
                if (editing == 1) {
                    OutlinedButton(
                        onClick = {
                            onUseCurrentLocation()
                            if (canUseCurrentLocation) editing = 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canUseCurrentLocation
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (canUseCurrentLocation) "От моего местоположения" else "Сначала нажмите «Где я»")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (editing == 1) "Выберите остановку отправления" else "Выберите остановку назначения") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (filtered.isEmpty()) {
                        item { EmptySearchResult(query) }
                    }
                    items(filtered, key = { it.id }) { stop ->
                        ListItem(
                            headlineContent = { Text(stop.name) },
                            modifier = Modifier.clickable {
                                if (editing == 1) onSetFrom(stop) else onSetTo(stop)
                                editing = 0
                                query = ""
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else {
                Button(
                    onClick = onBuild,
                    enabled = from != null && to != null && !isPlanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPlanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Построить маршрут")
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                // Избранные (сохранённые) маршруты — открываются одним нажатием
                if (journey == null && savedJourneys.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Избранные маршруты", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(savedJourneys) { sj ->
                            ListItem(
                                leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text("${sj.fromName} → ${sj.toName}") },
                                trailingContent = {
                                    IconButton(onClick = { onDeleteSaved(sj) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                                    }
                                },
                                modifier = Modifier.clickable { onOpenSaved(sj) }
                            )
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                // Варианты поездки (несколько маршрутов на выбор)
                if (variants.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    Text("Варианты поездки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        variants.forEachIndexed { idx, v ->
                            val label = v.steps.filter { it.type == com.example.data.RoutePlanner.StepType.RIDE }
                                .joinToString(" → ") { it.routeNumber ?: "?" }
                            val sub = if (v.transfers == 0) "без пересадок" else "пересадок: ${v.transfers}"
                            FilterChip(
                                selected = idx == selectedVariant,
                                onClick = { onSelectVariant(idx) },
                                label = { Text("$label • ~${v.totalMinutes} мин ($sub)") }
                            )
                        }
                    }
                }

                journey?.let { j ->
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            JourneyStat("В пути", "~${j.totalMinutes} мин")
                            JourneyStat("Расстояние", "${"%.1f".format(j.totalDistanceMeters / 1000)} км")
                            JourneyStat("Пешком", formatDistance(j.walkMeters))
                            JourneyStat("Пересадки", j.transfers.toString())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Компактные кнопки: показать на карте + сохранить в избранное
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = onShowJourneyOnMap,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Показать на карте", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onSaveJourney,
                            enabled = !isCurrentSaved,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (isCurrentSaved) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = null, modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isCurrentSaved) "В избранном" else "В избранное", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(j.steps) { step ->
                            JourneyStepRow(step, onShowRouteOnMap)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndpointField(label: String, value: String?, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value ?: "выбрать остановку",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
                color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun JourneyStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun JourneyStepRow(step: com.example.data.RoutePlanner.JourneyStep, onShowRouteOnMap: (String) -> Unit) {
    val isRide = step.type == com.example.data.RoutePlanner.StepType.RIDE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isRide && step.routeId != null) it.clickable { onShowRouteOnMap(step.routeId) } else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRide) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(step.routeColor), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(step.routeNumber ?: "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🚶", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (isRide) {
                Text("Автобус №${step.routeNumber}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("${step.fromStopName} → ${step.toStopName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${step.rideStopsCount} ост. · ~${step.durationMinutes} мин · нажмите, чтобы показать на карте", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Пешком", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("${step.fromStopName} → ${step.toStopName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${formatDistance(step.distanceMeters)} · ~${step.durationMinutes} мин", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Открывает системный экран уведомлений приложения.
 *
 * Свой экран настроек делать не стали: Android уже даёт по каналу отдельный
 * переключатель, и пассажир может оставить штормовые предупреждения, отключив
 * бытовые. Если системный экран почему-то недоступен, открываем общие
 * настройки приложения.
 */
private fun openNotificationSettings(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // системных настроек нет — тихо игнорируем
        }
    }
}
