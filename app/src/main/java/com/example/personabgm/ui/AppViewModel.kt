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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
) : ViewModel() {

    /** Null until DataStore has produced its first value, so the app never flashes the wrong theme. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val audioCache: StateFlow<CacheSnapshot> = cacheStatus.snapshot

    fun setTheme(theme: GameTheme) = launchSetting { this.setTheme(theme) }
    fun setHapticsEnabled(enabled: Boolean) = launchSetting { this.setHapticsEnabled(enabled) }
    fun setReduceMotion(enabled: Boolean) = launchSetting { this.setReduceMotion(enabled) }
    fun setOnboardingCompleted(done: Boolean) = launchSetting { this.setOnboardingCompleted(done) }
    fun setGlobalVolume(volume: Float) = launchSetting { this.setGlobalVolume(volume) }
    fun setCrossfadeDurationMs(ms: Int) = launchSetting { this.setCrossfadeDurationMs(ms) }
    fun setCrossfadeCurve(curve: CrossfadeCurve) = launchSetting { this.setCrossfadeCurve(curve) }
    fun setFastZoneDetection(enabled: Boolean) = launchSetting { this.setFastZoneDetection(enabled) }

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
