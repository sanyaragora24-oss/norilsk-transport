package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Хранит «Избранное» пользователя (остановки и маршруты) в DataStore.
 * Просто и надёжно: два набора строковых id.
 */
class FavoritesRepository(context: Context) {

    private val store = SafePreferenceStores.get(context, "favorites")

    private val favStopsKey = stringSetPreferencesKey("favorite_stops")
    private val favRoutesKey = stringSetPreferencesKey("favorite_routes")

    val favoriteStopIds: Flow<Set<String>> =
        store.safeData().map { it[favStopsKey] ?: emptySet() }

    val favoriteRouteIds: Flow<Set<String>> =
        store.safeData().map { it[favRoutesKey] ?: emptySet() }

    suspend fun toggleStop(stopId: String) {
        store.edit { prefs ->
            val current = prefs[favStopsKey]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(stopId)) current.remove(stopId) // add вернул false => уже был => убираем
            prefs[favStopsKey] = current
        }
    }

    suspend fun toggleRoute(routeId: String) {
        store.edit { prefs ->
            val current = prefs[favRoutesKey]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(routeId)) current.remove(routeId)
            prefs[favRoutesKey] = current
        }
    }
}
