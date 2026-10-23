package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Хранит группы избранного («На работу», «На дачу» …) в DataStore как JSON-список.
 * Каждый маршрут/остановка может входить в любое число групп (или ни в одну).
 */
class FavoriteGroupsRepository(context: Context) {

    private val store = SafePreferenceStores.get(context, "favorite_groups")

    private val key = stringPreferencesKey("groups_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(FavoriteGroup.serializer())

    val groups: Flow<List<FavoriteGroup>> = store.safeData().map { prefs ->
        decode(prefs[key])
    }

    private fun decode(raw: String?): List<FavoriteGroup> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun update(transform: (List<FavoriteGroup>) -> List<FavoriteGroup>) {
        store.edit { prefs ->
            val current = prefs.decodeListPreservingUnreadable(key) { json.decodeFromString(serializer, it) }
            prefs[key] = json.encodeToString(serializer, transform(current))
        }
    }

    suspend fun createGroup(name: String) = update { list ->
        list + FavoriteGroup(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Без названия" }
        )
    }

    suspend fun renameGroup(id: String, name: String) = update { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
    }

    suspend fun deleteGroup(id: String) = update { list ->
        list.filterNot { it.id == id }
    }

    suspend fun toggleStopInGroup(groupId: String, stopId: String) = update { list ->
        list.map { g ->
            if (g.id != groupId) g
            else if (g.stopIds.contains(stopId)) g.copy(stopIds = g.stopIds - stopId)
            else g.copy(stopIds = g.stopIds + stopId)
        }
    }

    suspend fun toggleRouteInGroup(groupId: String, routeId: String) = update { list ->
        list.map { g ->
            if (g.id != groupId) g
            else if (g.routeIds.contains(routeId)) g.copy(routeIds = g.routeIds - routeId)
            else g.copy(routeIds = g.routeIds + routeId)
        }
    }
}
