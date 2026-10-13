package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Сохранённый («в избранное») построенный маршрут А→Б. */
@Serializable
data class SavedJourney(
    val fromStopId: String,
    val toStopId: String,
    val fromName: String,
    val toName: String
)

/**
 * Хранит построенные маршруты, добавленные пользователем в избранное (DataStore, JSON-список).
 */
class SavedJourneysRepository(context: Context) {

    private val store = SafePreferenceStores.get(context, "saved_journeys")

    private val key = stringPreferencesKey("saved_journeys_json")
    private val json = Json { ignoreUnknownKeys = true }

    val savedJourneys: Flow<List<SavedJourney>> =
        store.safeData().map { prefs ->
            prefs[key]?.let {
                try { json.decodeFromString<List<SavedJourney>>(it) } catch (e: Exception) { emptyList() }
            } ?: emptyList()
        }

    suspend fun add(journey: SavedJourney) {
        store.edit { prefs ->
            val current = prefs.decodeListPreservingUnreadable(key, ::decodeStrict).toMutableList()
            // без дублей по паре остановок
            if (current.none { it.fromStopId == journey.fromStopId && it.toStopId == journey.toStopId }) {
                current.add(journey)
            }
            prefs[key] = json.encodeToString(current)
        }
    }

    suspend fun remove(fromStopId: String, toStopId: String) {
        store.edit { prefs ->
            val current = prefs.decodeListPreservingUnreadable(key, ::decodeStrict).filterNot {
                it.fromStopId == fromStopId && it.toStopId == toStopId
            }
            prefs[key] = json.encodeToString(current)
        }
    }

    private fun decodeStrict(raw: String): List<SavedJourney> = json.decodeFromString<List<SavedJourney>>(raw)
}
