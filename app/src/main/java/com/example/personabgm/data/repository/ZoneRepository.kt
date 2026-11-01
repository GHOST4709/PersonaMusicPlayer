package com.example.personabgm.data.repository

import com.example.personabgm.data.local.db.ZoneDao
import com.example.personabgm.data.local.db.ZoneEntity
import com.example.personabgm.data.local.db.ZoneStateDao
import com.example.personabgm.domain.model.LatLng
import com.example.personabgm.domain.model.Zone
import com.example.personabgm.domain.model.ZoneRules
import com.example.personabgm.geofence.GeofenceManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface AddZoneResult {
    data class Added(val zone: Zone) : AddZoneResult
    data object LimitReached : AddZoneResult
    data object InvalidLocation : AddZoneResult
}

/**
 * Zones are stored in Room; every change re-syncs the registered geofences so the two never drift apart.
 * The validation rules live in [ZoneRules].
 */
@Singleton
class ZoneRepository @Inject constructor(
    private val zoneDao: ZoneDao,
    private val zoneStateDao: ZoneStateDao,
    private val geofenceManager: GeofenceManager,
) {
    fun observeAll(): Flow<List<Zone>> = zoneDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(location: LatLng, nowMs: Long = System.currentTimeMillis()): AddZoneResult {
        if (!ZoneRules.isValidLocation(location.lat, location.lng)) return AddZoneResult.InvalidLocation
        if (!ZoneRules.canAddAnother(zoneDao.count())) return AddZoneResult.LimitReached

        val entity = ZoneEntity(
            id = UUID.randomUUID().toString(),
            name = ZoneRules.nextDefaultName(zoneDao.getNames()),
            lat = location.lat,
            lng = location.lng,
            radiusM = ZoneRules.DEFAULT_RADIUS_M,
            assignedTrackId = null,
            enabled = true,
            createdAt = nowMs,
            updatedAt = nowMs,
        )
        zoneDao.upsert(entity)
        geofenceManager.sync()
        return AddZoneResult.Added(entity.toDomain())
    }

    suspend fun setRadius(id: String, radiusM: Float) =
        change(id) { it.copy(radiusM = ZoneRules.clampRadius(radiusM)) }

    suspend fun setAssignedTrack(id: String, trackId: String?) =
        change(id) { it.copy(assignedTrackId = trackId) }

    suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) change(id) { it.copy(name = trimmed.take(MAX_NAME_LENGTH)) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        change(id) { it.copy(enabled = enabled) }
        // A disabled zone is no longer registered, so any "inside" state it had is meaningless.
        if (!enabled) zoneStateDao.delete(id)
    }

    suspend fun delete(id: String) {
        zoneDao.deleteById(id) // zone_state rows go with it (foreign key cascade)
        geofenceManager.sync()
    }

    private suspend fun change(id: String, transform: (ZoneEntity) -> ZoneEntity) {
        val current = zoneDao.getById(id) ?: return
        zoneDao.upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
        geofenceManager.sync()
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
