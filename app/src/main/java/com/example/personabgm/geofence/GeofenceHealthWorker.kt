package com.example.personabgm.geofence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * The safety net of spec 5.2: about every 12 hours, re-register the geofences. It covers the cases nothing
 * else notices (an OEM clearing registrations, Play services data being cleared, a missed boot event).
 */
class GeofenceHealthWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun geofenceManager(): GeofenceManager
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
            .geofenceManager()
            .sync()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "geofence_health"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GeofenceHealthWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
