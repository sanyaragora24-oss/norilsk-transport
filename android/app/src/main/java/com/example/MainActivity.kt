package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppTheme
import com.example.ui.map.MapScreen
import com.example.ui.map.MapViewModel
import com.example.ui.support.SupportScreen
import com.example.ui.theme.NorilskTransitTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.yandex.mapkit.MapKitFactory

class MainActivity : ComponentActivity() {

    private val viewModel: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StartupGuard.run {
            val projectId = FirebaseApp.getInstance().options.projectId
            StartupGuard.runIfConfigured(projectId) {
                val messaging = FirebaseMessaging.getInstance()
                messaging.isAutoInitEnabled = true
                messaging.subscribeToTopic("norilsk_storm")
                    .addOnFailureListener {
                        Log.w("FirebaseMessaging", "Storm topic subscription failed")
                    }
            }
        }

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val navController = rememberNavController()

            NorilskTransitTheme(
                darkTheme = when (uiState.appTheme) {
                    AppTheme.LIGHT -> false
                    AppTheme.DARK -> true
                    AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                NavHost(navController = navController, startDestination = "map") {
                    composable("map") {
                        MapScreen(
                            viewModel = viewModel,
                            onNavigateToSupport = { navController.navigate("support") }
                        )
                    }
                    composable("support") {
                        SupportScreen(
                            onBack = { navController.popBackStack() },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            MapKitFactory.getInstance().onStart()
        } catch (e: Exception) {
            Log.e("MapKit", "Error onStart MapKit", e)
        }
    }

    override fun onStop() {
        try {
            MapKitFactory.getInstance().onStop()
        } catch (e: Exception) {
            Log.e("MapKit", "Error onStop MapKit", e)
        }
        super.onStop()
    }
}
