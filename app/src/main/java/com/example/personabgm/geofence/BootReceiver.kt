package com.example.personabgm.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-registers geofences after a reboot or an app update (spec 5.2); the platform forgets them both times.
 *
 * Deliberately does NOT start the playback service: Android 15 forbids launching media-playback
 * foreground services from BOOT_COMPLETED. Music resumes on the next geofence event or when the app opens.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var geofenceManager: GeofenceManager

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Hilt injects here
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        scope.launch {
            try {
                geofenceManager.sync()
            } catch (e: Exception) {
                Log.e(TAG, "Geofence re-registration failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
