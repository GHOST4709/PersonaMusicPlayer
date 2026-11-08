package com.example.personabgm

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.personabgm.geofence.ForegroundProximityMonitor
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
    @Inject lateinit var proximityMonitor: ForegroundProximityMonitor

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate() // Hilt injects here
        GeofenceHealthWorker.schedule(this)
        // Re-register on every process start: cheap, idempotent, and it repairs a lost registration.
        appScope.launch { geofenceManager.sync() }

        // Fast zone detection runs only while the app is on screen (spec 5.2 optional fast path).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = proximityMonitor.start()
            override fun onStop(owner: LifecycleOwner) = proximityMonitor.stop()
        })
    }
}
