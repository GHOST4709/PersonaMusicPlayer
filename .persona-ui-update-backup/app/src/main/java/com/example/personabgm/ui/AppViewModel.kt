package com.example.personabgm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personabgm.audio.CacheSnapshot
import com.example.personabgm.audio.CacheStatus
import com.example.personabgm.audio.PrecacheScheduler
import com.example.personabgm.data.local.datastore.SettingsRepository
import com.example.personabgm.data.repository.ManifestRepository
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.domain.model.CrossfadeCurve
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.security.MusicAccessTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val manifestRepository: ManifestRepository,
    private val cacheStatus: CacheStatus,
    private val precacheScheduler: PrecacheScheduler,
    private val musicAccessTokenStore: MusicAccessTokenStore,
) : ViewModel() {

    /** Null until DataStore has produced its first value, so the app never flashes the wrong theme. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val audioCache: StateFlow<CacheSnapshot> = cacheStatus.snapshot
    private val _musicAccessConfigured = MutableStateFlow(false)
    val musicAccessConfigured: StateFlow<Boolean> = _musicAccessConfigured.asStateFlow()
    private val _musicAccessError = MutableStateFlow<String?>(null)
    val musicAccessError: StateFlow<String?> = _musicAccessError.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _musicAccessConfigured.value = musicAccessTokenStore.getToken() != null
        }
    }

    fun setTheme(theme: GameTheme) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
            precacheScheduler.schedule()
        }
    }
    fun setHapticsEnabled(enabled: Boolean) = launchSetting { this.setHapticsEnabled(enabled) }
    fun setReduceMotion(enabled: Boolean) = launchSetting { this.setReduceMotion(enabled) }
    fun setOnboardingCompleted(done: Boolean) = launchSetting { this.setOnboardingCompleted(done) }
    fun setGlobalVolume(volume: Float) = launchSetting { this.setGlobalVolume(volume) }
    fun setCrossfadeDurationMs(ms: Int) = launchSetting { this.setCrossfadeDurationMs(ms) }
    fun setCrossfadeCurve(curve: CrossfadeCurve) = launchSetting { this.setCrossfadeCurve(curve) }
    fun setFastZoneDetection(enabled: Boolean) = launchSetting { this.setFastZoneDetection(enabled) }
    fun setAudioCacheLimitMb(value: Int) = launchSetting { this.setAudioCacheLimitMb(value) }

    fun saveMusicAccessToken(token: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { musicAccessTokenStore.save(token) }
                _musicAccessConfigured.value = true
                _musicAccessError.value = null
                onSaved()
            } catch (error: Exception) {
                _musicAccessError.value = error.message ?: "Could not save the private music access token."
            }
        }
    }

    fun clearMusicAccessToken() {
        viewModelScope.launch(Dispatchers.IO) { musicAccessTokenStore.clear() }
        _musicAccessConfigured.value = false
        _musicAccessError.value = null
    }

    fun setPrecacheWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrecacheWifiOnly(enabled)
            precacheScheduler.schedule() // re-schedule with the new network constraint
        }
    }

    fun refreshAudioCache() {
        viewModelScope.launch { cacheStatus.refresh(manifestRepository.manifest.first()) }
    }

    fun clearAudioCache() {
        viewModelScope.launch { cacheStatus.clearAll(manifestRepository.manifest.first()) }
    }

    private fun launchSetting(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }
}
