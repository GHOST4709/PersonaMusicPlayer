package com.example.personabgm.geofence

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.location.Location
import android.os.Looper
import android.util.Log
import com.example.personabgm.context.LocationFix
import com.example.personabgm.context.ProximityEvent
import com.example.personabgm.context.ProximityTracker
import com.example.personabgm.context.ProximityZone
import com.example.personabgm.data.local.datastore.SettingsRepository
import com.example.personabgm.data.local.db.ZoneDao
import com.example.personabgm.data.local.db.ZoneStateDao
import com.example.personabgm.data.local.db.ZoneStateEntity
import com.example.personabgm.domain.model.ZoneStateValue
import com.example.personabgm.util.awaitResult
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

/**
 * The optional fast path of spec 5.2. While the app is on screen it watches the device's own location and
 * writes the same "inside / outside" zone state the geofence receiver writes, so arriving and leaving are
 * noticed in seconds instead of a minute or more. The decisions live in the tested, pure [ProximityTracker].
 *
 * It only runs while the app is visible (started and stopped from the process lifecycle) and only with
 * precise location permission, so it adds no background battery cost. The real geofences keep working
 * underneath either way, and writing the same state twice is harmless.
 */
@Singleton
class ForegroundProximityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zoneDao: ZoneDao,
    private val zoneStateDao: ZoneStateDao,
    private val settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val tracker = ProximityTracker()
    private val lock = Any()

    @Volatile private var zones: List<ProximityZone> = emptyList()
    @Volatile private var insideNow: Set<String> = emptySet()
    private var job: Job? = null

    /** The app came to the foreground. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            settingsRepository.settings.map { it.fastZoneDetection }.distinctUntilChanged().collectLatest { enabled ->
                if (enabled) watch()
            }
        }
    }

    /** The app left the foreground. */
    fun stop() {
        job?.cancel()
        job = null
        synchronized(lock) { tracker.reset() }
    }

    private suspend fun watch() = coroutineScope {
        // Permission can be granted while the app is open, so wait for it rather than give up.
        while (!locationProvider.hasFinePermission()) delay(PERMISSION_POLL_MS)

        val zoneData = launch {
            combine(zoneDao.observeAll(), zoneStateDao.observeAll()) { zoneRows, states ->
                val watched = zoneRows.filter { it.enabled }.map { ProximityZone(it.id, it.lat, it.lng, it.radiusM) }
                val inside = states.filter { it.state == ZoneStateValue.INSIDE }.map { it.zoneId }.toSet()
                watched to inside
            }.collect { (watched, inside) ->
                zones = watched
                insideNow = inside
            }
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { onLocation(it) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper()).awaitResult()
            awaitCancellation()
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission was revoked; fast zone detection is off", e)
        } finally {
            client.removeLocationUpdates(callback)
            zoneData.cancel()
        }
    }

    private fun onLocation(location: Location) {
        if (!location.hasAccuracy()) return
        val fix = LocationFix(location.latitude, location.longitude, location.accuracy, System.currentTimeMillis())
        val events = synchronized(lock) { tracker.onFix(fix, zones, insideNow) }
        for (event in events) scope.launch { record(event) }
    }

    private suspend fun record(event: ProximityEvent) {
        val state = when (event) {
            is ProximityEvent.Dwell -> ZoneStateValue.INSIDE
            is ProximityEvent.Exit -> ZoneStateValue.OUTSIDE
        }
        try {
            zoneStateDao.upsert(ZoneStateEntity(event.zoneId, state, event.atMs))
        } catch (e: SQLiteConstraintException) {
            // The zone was deleted a moment ago; nothing to record.
        }
    }

    private companion object {
        const val TAG = "ProximityMonitor"
        const val UPDATE_INTERVAL_MS = 5_000L
        const val MIN_UPDATE_INTERVAL_MS = 2_000L
        const val PERMISSION_POLL_MS = 10_000L
    }
}
