package com.example.personabgm.context

import android.util.Log
import com.example.personabgm.data.local.datastore.SettingsRepository
import com.example.personabgm.data.local.db.ZoneDao
import com.example.personabgm.data.local.db.ZoneStateDao
import com.example.personabgm.data.repository.ManifestRepository
import com.example.personabgm.data.repository.WeatherRepository
import com.example.personabgm.data.repository.toDomain
import com.example.personabgm.domain.model.AutoContext
import com.example.personabgm.domain.model.ContextInfo
import com.example.personabgm.domain.model.LatLng
import com.example.personabgm.domain.model.PlaybackContext
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.WeatherType
import com.example.personabgm.domain.model.ZoneStateValue
import com.example.personabgm.domain.resolver.ContextResolver
import com.example.personabgm.geofence.LocationProvider
import com.example.personabgm.util.debounceAfterFirst
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * Turns zones, geofence state, weather, time of day, the active theme, and the manifest into one stream
 * of [AutoContext]: what AUTO mode should be playing and why (spec 5.3 to 5.6).
 *
 * The decisions themselves are in pure, tested classes ([ContextResolver], [ZoneTracker], [WeatherPolicy],
 * [TimeBuckets]); this class only wires them to Room, the clock, and the network.
 *
 * Threading: collected on the service's main-thread scope, which is what keeps [ZoneTracker] safe to touch
 * from both the state collector and its deadline timer.
 */
class ContextEngine(
    private val scope: CoroutineScope,
    settingsRepository: SettingsRepository,
    manifestRepository: ManifestRepository,
    private val zoneDao: ZoneDao,
    zoneStateDao: ZoneStateDao,
    private val weatherRepository: WeatherRepository,
    private val locationProvider: LocationProvider,
    /** Emits when the system clock, date, or time zone changes. */
    timeChanges: Flow<Unit>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val settings = settingsRepository.settings
    private val manifest = manifestRepository.manifest.distinctUntilChanged()
    private val zones = zoneDao.observeAll()

    private val signals: Flow<List<ZoneSignal>> = zoneStateDao.observeAll().map { rows ->
        rows.map { ZoneSignal(it.zoneId, it.state == ZoneStateValue.INSIDE, it.sinceEpochMs) }
    }

    /**
     * The zone driving the music (null = roaming). A SharedFlow with replay 1 rather than a StateFlow, so
     * [autoContext] waits for the real first value instead of briefly assuming "roaming" at start-up.
     */
    val activeZoneId: SharedFlow<String?> = activeZoneFlow().shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val weather: Flow<WeatherType> =
        combine(weatherRepository.snapshot, ticker(STALENESS_CHECK_MS)) { snapshot, _ ->
            WeatherPolicy.effective(snapshot, now())
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timeBucket: Flow<TimeBucket> = merge(flowOf(Unit), timeChanges)
        .transformLatest {
            // Restarted on every clock change, so the timer below always aims at the next real boundary.
            emit(Unit)
            while (true) {
                delay(TimeBuckets.millisUntilNextBoundary(ZonedDateTime.now()))
                emit(Unit)
            }
        }
        .map { TimeBuckets.at(ZonedDateTime.now().toLocalTime()) }
        .distinctUntilChanged()

    /** Debounced 1 s after the first value (spec 5.6) and de-duplicated, so identical results never re-trigger a fade. */
    val autoContext: Flow<AutoContext> = run {
        val core = combine(settings, manifest, zones) { s, m, z -> Triple(s, m, z) }
        combine(core, activeZoneId, weather, timeBucket) { (appSettings, manifestOrNull, zoneEntities), zoneId, currentWeather, time ->
            val zone = zoneEntities.firstOrNull { it.id == zoneId && it.enabled }?.toDomain()
            // With the weather override off, weather is treated as clear (spec 5.4 fallback).
            val effectiveWeather = if (appSettings.weatherOverrideEnabled) currentWeather else WeatherType.CLEAR
            val info = ContextInfo(zone?.id, zone?.name, effectiveWeather, time, appSettings.theme)
            val target = manifestOrNull?.let {
                ContextResolver.resolve(PlaybackContext(zone, appSettings.theme, effectiveWeather, time), it)
            }
            AutoContext(target, info)
        }
    }.debounceAfterFirst(DEBOUNCE_MS).distinctUntilChanged()

    /** Starts the weather refresh loop. Call once after construction. */
    fun start() {
        scope.launch {
            merge(activeZoneId.map { }, ticker(WEATHER_TICK_MS)).collect {
                try {
                    refreshWeather()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Weather refresh failed", e)
                }
            }
        }
    }

    private suspend fun refreshWeather() {
        if (!settings.first().weatherOverrideEnabled) return
        val location = weatherLocation() ?: return
        weatherRepository.refreshIfNeeded(location, now())
    }

    /** The active zone's centre when inside one, otherwise the device's last known position. */
    private suspend fun weatherLocation(): LatLng? {
        val zoneId = activeZoneId.replayCache.firstOrNull()
        val zone = zoneId?.let { zoneDao.getById(it) }
        return if (zone != null) LatLng(zone.lat, zone.lng) else locationProvider.lastKnown()
    }

    private fun activeZoneFlow(): Flow<String?> = channelFlow {
        val tracker = ZoneTracker()
        var deadlines: Job? = null
        signals.collect { list ->
            tracker.update(list, now())
            send(tracker.activeZoneId())

            // Apply each pending exit when its grace window ends, then publish the new active zone.
            deadlines?.cancel()
            deadlines = launch {
                while (true) {
                    val due = tracker.nextDeadlineMs() ?: break
                    delay((due - now()).coerceAtLeast(0L))
                    tracker.advance(now())
                    send(tracker.activeZoneId())
                }
            }
        }
    }.distinctUntilChanged()

    private fun ticker(periodMs: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMs)
        }
    }

    private companion object {
        const val TAG = "ContextEngine"
        const val DEBOUNCE_MS = 1_000L
        const val WEATHER_TICK_MS = 5 * 60 * 1000L
        const val STALENESS_CHECK_MS = 5 * 60 * 1000L
    }
}
