package com.example.personabgm.ui.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personabgm.data.local.datastore.ScenarioRepository
import com.example.personabgm.data.repository.ManifestRepository
import com.example.personabgm.data.repository.ZoneRepository
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.MusicScenario
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScenarioManagerState(
    val scenarios: List<MusicScenario> = emptyList(),
    val manifest: Manifest? = null,
    val zones: List<Zone> = emptyList(),
) {
    val tracks: List<Track> get() = manifest?.tracks.orEmpty()
}

@HiltViewModel
class ScenarioViewModel @Inject constructor(
    private val scenarioRepository: ScenarioRepository,
    manifestRepository: ManifestRepository,
    zoneRepository: ZoneRepository,
) : ViewModel() {
    val state: StateFlow<ScenarioManagerState> = combine(
        scenarioRepository.scenarios,
        manifestRepository.manifest,
        zoneRepository.observeAll(),
    ) { scenarios, manifest, zones ->
        ScenarioManagerState(
            scenarios = scenarios.sortedWith(compareByDescending<MusicScenario> { it.priority }.thenByDescending { it.createdAt }),
            manifest = manifest,
            zones = zones,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScenarioManagerState())

    fun save(scenario: MusicScenario) {
        viewModelScope.launch { scenarioRepository.save(scenario) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { scenarioRepository.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch { scenarioRepository.delete(id) }
    }
}
