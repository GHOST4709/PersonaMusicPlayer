package com.example.personabgm.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personabgm.audio.PrecacheScheduler
import com.example.personabgm.data.local.db.ZoneStateDao
import com.example.personabgm.data.repository.AddZoneResult
import com.example.personabgm.data.repository.ZoneRepository
import com.example.personabgm.domain.model.LatLng
import com.example.personabgm.domain.model.Zone
import com.example.personabgm.domain.model.ZoneRules
import com.example.personabgm.domain.model.ZoneStateValue
import com.example.personabgm.geofence.GeofenceManager
import com.example.personabgm.geofence.GeofenceStatus
import com.example.personabgm.geofence.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ZoneRow(val zone: Zone, val inside: Boolean)

data class LocationAccess(val fine: Boolean, val background: Boolean)

@HiltViewModel
class ZonesViewModel @Inject constructor(
    private val zoneRepository: ZoneRepository,
    zoneStateDao: ZoneStateDao,
    private val geofenceManager: GeofenceManager,
    private val locationProvider: LocationProvider,
    private val precacheScheduler: PrecacheScheduler,
) : ViewModel() {

    val rows: StateFlow<List<ZoneRow>> =
        combine(zoneRepository.observeAll(), zoneStateDao.observeAll()) { zones, states ->
            val inside = states.filter { it.state == ZoneStateValue.INSIDE }.map { it.zoneId }.toSet()
            zones.map { ZoneRow(it, it.id in inside) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val geofenceStatus: StateFlow<GeofenceStatus> = geofenceManager.status

    private val _access = MutableStateFlow(readAccess())
    val access: StateFlow<LocationAccess> = _access.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    /** Where the device is, to centre the map when there are no zones yet. */
    private val _deviceLocation = MutableStateFlow<LatLng?>(null)
    val deviceLocation: StateFlow<LatLng?> = _deviceLocation.asStateFlow()

    fun select(id: String?) {
        _selectedId.value = id
    }

    fun loadDeviceLocation() {
        viewModelScope.launch { _deviceLocation.value = locationProvider.lastKnown() ?: locationProvider.current() }
    }

    /** Call on resume and after a permission result; registers geofences as soon as location is granted. */
    fun refreshAccess() {
        val latest = readAccess()
        val gainedLocation = latest.fine && !_access.value.fine
        _access.value = latest
        if (gainedLocation) refreshGeofences()
    }

    fun refreshGeofences() {
        viewModelScope.launch { geofenceManager.sync() }
    }

    fun addZoneHere() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val location = locationProvider.current()
            if (location == null) {
                _message.value = "Couldn't get your location. Check that location is on and allowed."
            } else {
                add(location, "at your location")
            }
            _busy.value = false
        }
    }

    /** Press-and-hold on the map drops a zone exactly there. */
    fun addZoneAt(lat: Double, lng: Double) {
        viewModelScope.launch { add(LatLng(lat, lng), "on the map") }
    }

    private suspend fun add(location: LatLng, where: String) {
        _message.value = when (val result = zoneRepository.add(location)) {
            is AddZoneResult.Added -> {
                _selectedId.value = result.zone.id
                "Added ${result.zone.name} $where."
            }
            AddZoneResult.LimitReached -> "You've reached the limit of ${ZoneRules.MAX_ZONES} zones."
            AddZoneResult.InvalidLocation -> "That location can't be used for a zone."
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { zoneRepository.rename(id, name) }
    }

    fun setRadius(id: String, radiusM: Float) {
        viewModelScope.launch { zoneRepository.setRadius(id, radiusM) }
    }

    fun assignTrack(id: String, trackId: String?) {
        viewModelScope.launch {
            zoneRepository.setAssignedTrack(id, trackId)
            precacheScheduler.schedule() // download the newly chosen track ahead of time (spec 4.4)
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { zoneRepository.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        if (_selectedId.value == id) _selectedId.value = null
        viewModelScope.launch { zoneRepository.delete(id) }
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun readAccess() = LocationAccess(
        fine = locationProvider.hasFinePermission(),
        background = locationProvider.hasBackgroundPermission(),
    )
}
