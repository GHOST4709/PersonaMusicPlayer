package com.example.personabgm.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.domain.model.CrossfadeCurve
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.PlaybackScene
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun setTheme(theme: GameTheme) = update { it[Keys.THEME] = theme.name }
    suspend fun setPlaybackScene(scene: PlaybackScene) = update { it[Keys.PLAYBACK_SCENE] = scene.name }
    suspend fun setGlobalVolume(volume: Float) = update { it[Keys.GLOBAL_VOLUME] = volume.coerceIn(0f, 1f) }
    suspend fun setCrossfadeDurationMs(ms: Int) = update { it[Keys.CROSSFADE_DURATION_MS] = ms.coerceIn(500, 8000) }
    suspend fun setCrossfadeCurve(curve: CrossfadeCurve) = update { it[Keys.CROSSFADE_CURVE] = curve.name }
    suspend fun setWeatherOverrideEnabled(enabled: Boolean) = update { it[Keys.WEATHER_OVERRIDE] = enabled }
    suspend fun setPrecacheWifiOnly(enabled: Boolean) = update { it[Keys.PRECACHE_WIFI_ONLY] = enabled }
    suspend fun setAudioCacheLimitMb(value: Int) = update { it[Keys.AUDIO_CACHE_LIMIT_MB] = value.coerceIn(256, 2048) }
    suspend fun setFastZoneDetection(enabled: Boolean) = update { it[Keys.FAST_ZONE_DETECTION] = enabled }
    suspend fun setHapticsEnabled(enabled: Boolean) = update { it[Keys.HAPTICS] = enabled }
    suspend fun setReduceMotion(enabled: Boolean) = update { it[Keys.REDUCE_MOTION] = enabled }
    suspend fun setOnboardingCompleted(done: Boolean) = update { it[Keys.ONBOARDING_COMPLETED] = done }

    /**
     * Whether the person wants music playing right now. Kept outside [AppSettings] on purpose: it flips on
     * every Play/Pause and would otherwise recompose the whole UI. The geofence receiver reads it to decide
     * whether to bring the service back after the OS killed it.
     */
    suspend fun setPlaybackActive(active: Boolean) = update { it[Keys.PLAYBACK_ACTIVE] = active }

    suspend fun isPlaybackActive(): Boolean = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .first()[Keys.PLAYBACK_ACTIVE] ?: false

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private object Keys {
        val THEME = stringPreferencesKey("active_theme")
        val PLAYBACK_SCENE = stringPreferencesKey("playback_scene")
        val GLOBAL_VOLUME = floatPreferencesKey("global_volume")
        val CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_duration_ms")
        val CROSSFADE_CURVE = stringPreferencesKey("crossfade_curve")
        val WEATHER_OVERRIDE = booleanPreferencesKey("weather_override_enabled")
        val PRECACHE_WIFI_ONLY = booleanPreferencesKey("precache_wifi_only")
        val AUDIO_CACHE_LIMIT_MB = intPreferencesKey("audio_cache_limit_mb")
        val FAST_ZONE_DETECTION = booleanPreferencesKey("fast_zone_detection")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PLAYBACK_ACTIVE = booleanPreferencesKey("playback_active")
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = GameTheme.fromNameOrDefault(this[Keys.THEME]),
            playbackScene = PlaybackScene.fromNameOrDefault(this[Keys.PLAYBACK_SCENE]),
            globalVolume = (this[Keys.GLOBAL_VOLUME] ?: defaults.globalVolume).coerceIn(0f, 1f),
            crossfadeDurationMs = this[Keys.CROSSFADE_DURATION_MS] ?: defaults.crossfadeDurationMs,
            crossfadeCurve = CrossfadeCurve.entries.firstOrNull { it.name == this[Keys.CROSSFADE_CURVE] }
                ?: defaults.crossfadeCurve,
            weatherOverrideEnabled = this[Keys.WEATHER_OVERRIDE] ?: defaults.weatherOverrideEnabled,
            precacheWifiOnly = this[Keys.PRECACHE_WIFI_ONLY] ?: defaults.precacheWifiOnly,
            audioCacheLimitMb = (this[Keys.AUDIO_CACHE_LIMIT_MB] ?: defaults.audioCacheLimitMb).coerceIn(256, 2048),
            fastZoneDetection = this[Keys.FAST_ZONE_DETECTION] ?: defaults.fastZoneDetection,
            hapticsEnabled = this[Keys.HAPTICS] ?: defaults.hapticsEnabled,
            reduceMotion = this[Keys.REDUCE_MOTION] ?: defaults.reduceMotion,
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
        )
    }
}
