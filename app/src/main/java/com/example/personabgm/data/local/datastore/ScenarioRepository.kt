package com.example.personabgm.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personabgm.domain.model.MusicScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists user-authored rules in the app's existing Preferences DataStore. This avoids a Room
 * schema migration for a feature that is still being shaped, while keeping all scenario data local.
 */
@Singleton
class ScenarioRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val scenarios: Flow<List<MusicScenario>> = dataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { preferences -> decode(preferences[SCENARIOS_JSON]) }

    suspend fun save(scenario: MusicScenario) {
        dataStore.edit { preferences ->
            val current = decode(preferences[SCENARIOS_JSON])
            val updated = scenario.copy(updatedAt = System.currentTimeMillis())
            preferences[SCENARIOS_JSON] = json.encodeToString(
                current.filterNot { it.id == updated.id } + updated,
            )
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = decode(preferences[SCENARIOS_JSON])
            preferences[SCENARIOS_JSON] = json.encodeToString(
                current.map { if (it.id == id) it.copy(enabled = enabled, updatedAt = System.currentTimeMillis()) else it },
            )
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            val current = decode(preferences[SCENARIOS_JSON])
            preferences[SCENARIOS_JSON] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    private fun decode(raw: String?): List<MusicScenario> =
        if (raw.isNullOrBlank()) emptyList() else runCatching {
            json.decodeFromString<List<MusicScenario>>(raw)
        }.getOrDefault(emptyList())

    private companion object {
        val SCENARIOS_JSON = stringPreferencesKey("music_scenarios_json_v1")
    }
}
