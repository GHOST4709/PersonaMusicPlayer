package com.example.personabgm.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.personabgm.data.local.db.ZoneDao
import com.example.personabgm.data.local.db.ZoneEntity
import com.example.personabgm.domain.model.ZoneRules
import com.example.personabgm.util.awaitResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface GeofenceStatus {
    data object Idle : GeofenceStatus
    data class Registered(val count: Int) : GeofenceStatus
    data object MissingPermission : GeofenceStatus
    data class Failed(val message: String) : GeofenceStatus
}

/**
 * Registers the enabled zones with Google Play services (spec 5.1, 5.2).
 *
 *  - DWELL with a 15,000 ms loitering delay is the track-switching trigger; EXIT ends it. ENTER is not
 *    requested: nothing uses it, and every event wakes the app.
 *  - [sync] is idempotent: it clears this app's geofences and registers the current set, so it is safe
 *    to call after every zone edit, on app start, after boot, and from the periodic health check.
 *  - INITIAL_TRIGGER_DWELL makes a zone the person is already standing in report a DWELL.
 *  - Geofences are lost on reboot, app data clear, and "location not available" errors, which is why
 *    those paths all call [sync] again.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zoneDao: ZoneDao,
    private val locationProvider: LocationProvider,
) {
    private val client by lazy { LocationServices.getGeofencingClient(context) }
    private val mutex = Mutex()

    private val _status = MutableStateFlow<GeofenceStatus>(GeofenceStatus.Idle)
    val status: StateFlow<GeofenceStatus> = _status.asStateFlow()

    // Play services fills in the event extras, so the PendingIntent must be mutable on Android 12+.
    private val pendingIntent: PendingIntent by lazy {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        PendingIntent.getBroadcast(context, REQUEST_CODE, Intent(context, GeofenceReceiver::class.java), flags)
    }

    suspend fun sync(): GeofenceStatus = mutex.withLock {
        val result = register()
        _status.value = result
        result
    }

    /** Removes one zone's geofence, for a transition that arrives after the zone was deleted. */
    suspend fun remove(zoneId: String) {
        try {
            client.removeGeofences(listOf(zoneId)).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "removeGeofences($zoneId) failed", e)
        }
    }

    private suspend fun register(): GeofenceStatus {
        if (!locationProvider.hasFinePermission()) return GeofenceStatus.MissingPermission
        val zones = zoneDao.getEnabled()
        return try {
            client.removeGeofences(pendingIntent).awaitResult()
            if (zones.isEmpty()) return GeofenceStatus.Registered(0)

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .addGeofences(zones.map { it.toGeofence() })
                .build()
            client.addGeofences(request, pendingIntent).awaitResult()
            GeofenceStatus.Registered(zones.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            GeofenceStatus.MissingPermission
        } catch (e: ApiException) {
            Log.w(TAG, "Geofence registration failed: ${e.statusCode}", e)
            GeofenceStatus.Failed(GeofenceStatusCodes.getStatusCodeString(e.statusCode))
        } catch (e: Exception) {
            Log.w(TAG, "Geofence registration failed", e)
            GeofenceStatus.Failed(e.message ?: "Unknown error")
        }
    }

    private fun ZoneEntity.toGeofence(): Geofence = Geofence.Builder()
        .setRequestId(id)
        // The platform's reliable minimum is about 100 m, so never register anything smaller.
        .setCircularRegion(lat, lng, radiusM.coerceAtLeast(ZoneRules.MIN_RADIUS_M))
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT)
        .setLoiteringDelay(LOITERING_DELAY_MS)
        .build()

    private companion object {
        const val TAG = "GeofenceManager"
        const val REQUEST_CODE = 4100

        /** Spec 5.1: 15 s dwell before a track switch. */
        const val LOITERING_DELAY_MS = 15_000
    }
}
