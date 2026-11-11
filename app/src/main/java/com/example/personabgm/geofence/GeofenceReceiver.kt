package com.example.personabgm.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.example.personabgm.audio.PlaybackCommand
import com.example.personabgm.audio.PlaybackRemote
import com.example.personabgm.data.local.datastore.SettingsRepository
import com.example.personabgm.data.local.db.ZoneStateDao
import com.example.personabgm.data.local.db.ZoneStateEntity
import com.example.personabgm.domain.model.ZoneStateValue
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives geofence transitions from Google Play services (spec 5.2).
 *
 * It does exactly three things, quickly: **persist the transition first** (so nothing is lost if the
 * process dies), re-register on "not available" errors, and bring the playback service back if the
 * person was listening. The 15 s exit grace and everything else lives in the service's context engine,
 * not here, because a receiver only has a few seconds.
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var zoneStateDao: ZoneStateDao
    @Inject lateinit var geofenceManager: GeofenceManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playbackRemote: PlaybackRemote

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                handle(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle geofence event", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(event: GeofencingEvent) {
        if (event.hasError()) {
            Log.w(TAG, "Geofence error: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) geofenceManager.sync()
            return
        }

        val state = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_DWELL -> ZoneStateValue.INSIDE
            Geofence.GEOFENCE_TRANSITION_EXIT -> ZoneStateValue.OUTSIDE
            else -> return
        }

        val now = System.currentTimeMillis()
        for (geofence in event.triggeringGeofences.orEmpty()) {
            try {
                zoneStateDao.upsert(ZoneStateEntity(geofence.requestId, state, now))
            } catch (e: SQLiteConstraintException) {
                // The zone was deleted while the event was in flight: stop listening for it.
                geofenceManager.remove(geofence.requestId)
            }
        }

        // If the OS killed the service while the person was listening, bring it back. When the service is
        // alive it already observes the stored state and this is a harmless no-op.
        if (settingsRepository.isPlaybackActive()) {
            playbackRemote.sendFromBackground(PlaybackCommand.AutoResume)
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
