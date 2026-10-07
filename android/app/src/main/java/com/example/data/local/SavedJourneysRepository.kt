package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.savedJourneysStore by preferencesDataStore(name = "saved_journeys")

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
class SavedJourneysRepository(private val context: Context) {

    private val key = stringPreferencesKey("saved_journeys_json")
    private val json = Json { ignoreUnknownKeys = true }

    val savedJourneys: Flow<List<SavedJourney>> =
        context.savedJourneysStore.data.map { prefs ->
            prefs[key]?.let {
                try { json.decodeFromString<List<SavedJourney>>(it) } catch (e: Exception) { emptyList() }
            } ?: emptyList()
        }

    suspend fun add(journey: SavedJourney) {
        context.savedJourneysStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableList()
            // без дублей по паре остановок
            if (current.none { it.fromStopId == journey.fromStopId && it.toStopId == journey.toStopId }) {
                current.add(journey)
            }
            prefs[key] = json.encodeToString(current)
        }
    }

    suspend fun remove(fromStopId: String, toStopId: String) {
        context.savedJourneysStore.edit { prefs ->
            val current = decode(prefs[key]).filterNot {
                it.fromStopId == fromStopId && it.toStopId == toStopId
            }
            prefs[key] = json.encodeToString(current)
        }
    }

    private fun decode(raw: String?): List<SavedJourney> =
        raw?.let { try { json.decodeFromString<List<SavedJourney>>(it) } catch (e: Exception) { emptyList() } } ?: emptyList()
}
