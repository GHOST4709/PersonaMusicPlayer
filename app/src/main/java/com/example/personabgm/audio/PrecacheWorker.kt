package com.example.personabgm.audio

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.personabgm.data.local.datastore.SettingsRepository
import com.example.personabgm.data.local.db.ZoneDao
import com.example.personabgm.data.repository.ManifestRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Downloads the selected theme's default and zone-assigned tracks/variants. Zone tracks are
 * considered first and the selected theme default last, keeping the roaming default most recent.
 *
 * Uses a Hilt entry point instead of hilt-work to avoid a custom WorkManager configuration.
 */
class PrecacheWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun manifestRepository(): ManifestRepository
        fun mediaCache(): MediaCache
        fun cacheStatus(): CacheStatus
        fun zoneDao(): ZoneDao
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val manifest = deps.manifestRepository().manifest.first() ?: return Result.success()
        val cache = deps.mediaCache()

        var anyFailed = false
        val assigned = deps.zoneDao().getAssignedTrackIds()
        val activeTheme = deps.settingsRepository().settings.first().theme
        for (track in manifest.precacheTracks(assigned, activeTheme)) {
            if (cache.isFullyCached(track)) continue
            try {
                cache.download(track)
            } catch (e: IOException) {
                Log.w(TAG, "Pre-cache failed for ${track.id}", e)
                anyFailed = true
            }
        }
        deps.cacheStatus().refresh(manifest)

        // Retry with backoff, but give up after a few attempts so a permanently broken URL cannot loop forever.
        return if (anyFailed && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val TAG = "PrecacheWorker"
        const val MAX_ATTEMPTS = 5
    }
}

/** Enqueues the pre-cache job, honoring the "Wi-Fi only" setting (spec 3.3). */
@Singleton
class PrecacheScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun schedule() {
        val wifiOnly = settingsRepository.settings.first().precacheWifiOnly
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PrecacheWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE is cheap: a restarted download resumes from the partial copy already on disk.
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val UNIQUE_NAME = "precache_tracks"
    }
}
