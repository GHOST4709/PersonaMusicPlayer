package com.example.personabgm

import android.app.Application
import com.example.personabgm.geofence.GeofenceHealthWorker
import com.example.personabgm.geofence.GeofenceManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PersonaApp : Application() {

    @Inject lateinit var geofenceManager: GeofenceManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate() // Hilt injects here
        GeofenceHealthWorker.schedule(this)
        // Re-register on every process start: cheap, idempotent, and it repairs a lost registration.
        appScope.launch { geofenceManager.sync() }
    }
}
