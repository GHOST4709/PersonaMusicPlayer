package com.example.personabgm.ui.debug

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personabgm.BuildConfig
import com.example.personabgm.audio.CacheSnapshot
import com.example.personabgm.audio.CacheStatus
import com.example.personabgm.audio.PlaybackRemote
import com.example.personabgm.audio.PlaybackUiState
import com.example.personabgm.context.DebugOverrides
import com.example.personabgm.context.WeatherSnapshot
import com.example.personabgm.data.local.db.ManifestDao
import com.example.personabgm.data.local.db.ManifestMetaEntity
import com.example.personabgm.data.local.db.ZoneDao
import com.example.personabgm.data.local.db.ZoneEntity
import com.example.personabgm.data.local.db.ZoneStateDao
import com.example.personabgm.data.local.db.ZoneStateEntity
import com.example.personabgm.data.repository.WeatherRepository
import com.example.personabgm.domain.debug.DebugReport
import com.example.personabgm.domain.debug.DebugSection
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.WeatherType
import com.example.personabgm.domain.permissions.PermissionSnapshot
import com.example.personabgm.geofence.GeofenceManager
import com.example.personabgm.geofence.GeofenceStatus
import com.example.personabgm.geofence.LocationProvider
import com.example.personabgm.permissions.PermissionChecker
import com.example.personabgm.ui.formatAge
import com.example.personabgm.ui.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Debug builds only: a live window onto what the app is deciding, plus overrides to test weather and time. */
@HiltViewModel
class DebugViewModel @Inject constructor(
    remote: PlaybackRemote,
    private val weatherRepository: WeatherRepository,
    private val geofenceManager: GeofenceManager,
    zoneDao: ZoneDao,
    zoneStateDao: ZoneStateDao,
    cacheStatus: CacheStatus,
    private val permissionChecker: PermissionChecker,
    private val manifestDao: ManifestDao,
    private val locationProvider: LocationProvider,
    private val overrides: DebugOverrides,
) : ViewModel() {

    data class DebugUi(
        val playback: PlaybackUiState,
        val weather: WeatherSnapshot?,
        val geofence: GeofenceStatus,
        val cache: CacheSnapshot,
        val zones: List<ZoneEntity>,
        val zoneStates: List<ZoneStateEntity>,
        val weatherOverride: WeatherType?,
        val timeOverride: TimeBucket?,
        val permissions: PermissionSnapshot,
        val manifestMeta: ManifestMetaEntity?,
    )

    private data class Live(
        val playback: PlaybackUiState,
        val weather: WeatherSnapshot?,
        val geofence: GeofenceStatus,
        val cache: CacheSnapshot,
    )

    private data class Stored(
        val zones: List<ZoneEntity>,
        val zoneStates: List<ZoneStateEntity>,
        val weatherOverride: WeatherType?,
        val timeOverride: TimeBucket?,
    )

    private val permissions = MutableStateFlow(permissionChecker.snapshot())
    private val manifestMeta = MutableStateFlow<ManifestMetaEntity?>(null)

    private val live = combine(
        remote.state,
        weatherRepository.snapshot,
        geofenceManager.status,
        cacheStatus.snapshot,
    ) { playback, weather, geofence, cache -> Live(playback, weather, geofence, cache) }

    private val stored = combine(
        zoneDao.observeAll(),
        zoneStateDao.observeAll(),
        overrides.weather,
        overrides.time,
    ) { zones, states, weatherOverride, timeOverride -> Stored(zones, states, weatherOverride, timeOverride) }

    val ui: StateFlow<DebugUi?> = combine(live, stored, permissions, manifestMeta) { l, s, perms, meta ->
        DebugUi(
            playback = l.playback,
            weather = l.weather,
            geofence = l.geofence,
            cache = l.cache,
            zones = s.zones,
            zoneStates = s.zoneStates,
            weatherOverride = s.weatherOverride,
            timeOverride = s.timeOverride,
            permissions = perms,
            manifestMeta = meta,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh()
    }

    fun refresh() {
        permissions.value = permissionChecker.snapshot()
        viewModelScope.launch { manifestMeta.value = manifestDao.getMeta() }
    }

    fun setWeatherOverride(type: WeatherType?) {
        overrides.weather.value = type
    }

    fun setTimeOverride(bucket: TimeBucket?) {
        overrides.time.value = bucket
    }

    fun resyncGeofences() {
        viewModelScope.launch { geofenceManager.sync() }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            locationProvider.lastKnown()?.let { weatherRepository.refreshIfNeeded(it, force = true) }
        }
    }

    /** One block of plain text for a bug report. */
    fun report(ui: DebugUi, nowMs: Long = System.currentTimeMillis()): String {
        val p = ui.playback
        val ctx = p.context
        val stateById = ui.zoneStates.associateBy { it.zoneId }
        return DebugReport.format(
            listOf(
                DebugSection("Build", listOf("Persona BGM ${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "debug" else "release"})")),
                DebugSection("Device", listOf("${Build.MANUFACTURER} ${Build.MODEL}, Android API ${Build.VERSION.SDK_INT}")),
                DebugSection(
                    "Playback",
                    listOf(
                        "track: ${p.track?.let { "${it.title} (${it.id})" } ?: "none"}",
                        "playing=${p.isPlaying} buffering=${p.isBuffering} crossfading=${p.isCrossfading} mode=${p.mode}",
                        "error: ${p.error ?: "none"}",
                    ),
                ),
                DebugSection(
                    "Context",
                    listOf(
                        if (ctx == null) "none yet" else {
                            "zone=${ctx.zoneName ?: "roaming"} weather=${ctx.weather} time=${ctx.time} theme=${ctx.theme}"
                        },
                    ),
                ),
                DebugSection(
                    "Zones",
                    ui.zones.map { z ->
                        val s = stateById[z.id]
                        val state = s?.let { "${it.state} ${formatAge(nowMs - it.sinceEpochMs)}" } ?: "no state"
                        "${z.name} [${z.id.take(8)}] r=${z.radiusM.toInt()}m enabled=${z.enabled} track=${z.assignedTrackId ?: "theme default"} -> $state"
                    },
                ),
                DebugSection("Geofences", listOf(ui.geofence.toString())),
                DebugSection(
                    "Weather",
                    listOf(
                        ui.weather?.let { "${it.type} fetched ${formatAge(nowMs - it.fetchedAtMs)} at ${it.lat}, ${it.lng}" } ?: "no reading yet",
                        "override: ${ui.weatherOverride ?: "none"}",
                    ),
                ),
                DebugSection("Time", listOf("override: ${ui.timeOverride ?: "none"}")),
                DebugSection("Audio cache", listOf("${ui.cache.cachedTrackIds.size} tracks saved, ${formatBytes(ui.cache.totalBytes)} used")),
                DebugSection(
                    "Manifest",
                    listOf(
                        ui.manifestMeta?.let { "version ${it.version}, etag ${it.etag ?: "none"}, fetched ${formatAge(nowMs - it.fetchedAt)}" }
                            ?: "nothing cached",
                    ),
                ),
                DebugSection(
                    "Permissions",
                    listOf(
                        "precise location=${ui.permissions.fineLocation} approximate=${ui.permissions.coarseLocation} background=${ui.permissions.backgroundLocation}",
                        "notifications=${ui.permissions.notifications} batteryExempt=${ui.permissions.batteryExempt} bluetooth=${ui.permissions.bluetoothConnect}",
                        "locationServicesOn=${ui.permissions.locationServicesOn}",
                    ),
                ),
            ),
        )
    }
}
