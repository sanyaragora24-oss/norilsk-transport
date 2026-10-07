package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yandex.mapkit.map.MapType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class AppTheme { SYSTEM, LIGHT, DARK }

class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("app_theme")
    private val mapTypeKey = intPreferencesKey("map_type")

    val appTheme: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[themeKey] ?: AppTheme.SYSTEM.name
        AppTheme.valueOf(themeName)
    }

    val mapType: Flow<MapType> = context.dataStore.data.map { preferences ->
        val typeId = preferences[mapTypeKey] ?: MapType.MAP.ordinal
        MapType.values().firstOrNull { it.ordinal == typeId } ?: MapType.MAP
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[themeKey] = theme.name }
    }

    suspend fun setMapType(type: MapType) {
        context.dataStore.edit { it[mapTypeKey] = type.ordinal }
    }
}
