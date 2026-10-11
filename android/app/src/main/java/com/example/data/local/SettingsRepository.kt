package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yandex.mapkit.map.MapType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppTheme { SYSTEM, LIGHT, DARK }

class SettingsRepository(context: Context) {

    private val store = SafePreferenceStores.get(context, "settings")

    private val themeKey = stringPreferencesKey("app_theme")
    private val mapTypeKey = intPreferencesKey("map_type")

    val appTheme: Flow<AppTheme> = store.safeData().map { preferences ->
        val themeName = preferences[themeKey] ?: AppTheme.SYSTEM.name
        // Неизвестное значение (например, из будущей версии) не должно ронять запуск.
        AppTheme.entries.firstOrNull { it.name == themeName } ?: AppTheme.SYSTEM
    }

    val mapType: Flow<MapType> = store.safeData().map { preferences ->
        val typeId = preferences[mapTypeKey] ?: MapType.MAP.ordinal
        MapType.values().firstOrNull { it.ordinal == typeId } ?: MapType.MAP
    }

    suspend fun setTheme(theme: AppTheme) {
        store.edit { it[themeKey] = theme.name }
    }

    suspend fun setMapType(type: MapType) {
        store.edit { it[mapTypeKey] = type.ordinal }
    }
}
