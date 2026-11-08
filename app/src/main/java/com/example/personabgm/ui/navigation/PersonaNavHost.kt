package com.example.personabgm.ui.navigation

import android.os.Build
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.personabgm.BuildConfig
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.domain.model.CrossfadeCurve
import com.example.personabgm.domain.model.UiState
import com.example.personabgm.domain.permissions.HealthIssue
import com.example.personabgm.domain.permissions.OemGuidance
import com.example.personabgm.domain.permissions.PermissionHealth
import com.example.personabgm.ui.AppViewModel
import com.example.personabgm.ui.LibraryContent
import com.example.personabgm.ui.ManifestViewModel
import com.example.personabgm.ui.PlaybackViewModel
import com.example.personabgm.ui.debug.DebugScreen
import com.example.personabgm.ui.debug.DebugViewModel
import com.example.personabgm.ui.home.HomeScreen
import com.example.personabgm.ui.library.LibraryScreen
import com.example.personabgm.ui.onboarding.OnboardingScreen
import com.example.personabgm.ui.permissions.GpsBanner
import com.example.personabgm.ui.permissions.PermissionsViewModel
import com.example.personabgm.ui.permissions.rememberPermissionActions
import com.example.personabgm.ui.settings.SettingsScreen
import com.example.personabgm.ui.theme.LocalReduceMotion
import com.example.personabgm.ui.zones.ZonesScreen
import com.example.personabgm.ui.zones.ZonesViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ZONES = "zones"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
}

@Composable
fun PersonaNavHost(
    settings: AppSettings,
    viewModel: AppViewModel,
    manifestViewModel: ManifestViewModel,
    modifier: Modifier = Modifier,
    // Activity-scoped here, so every screen shares one adapter over the singleton PlaybackRemote.
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
    permissionsViewModel: PermissionsViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val reduceMotion = LocalReduceMotion.current
    val playback by playbackViewModel.state.collectAsStateWithLifecycle()
    val snapshot by permissionsViewModel.snapshot.collectAsStateWithLifecycle()
    val issues by permissionsViewModel.issues.collectAsStateWithLifecycle()
    val actions = rememberPermissionActions(onResult = permissionsViewModel::refresh)
    val musicAccessConfigured by viewModel.musicAccessConfigured.collectAsStateWithLifecycle()
    val musicAccessError by viewModel.musicAccessError.collectAsStateWithLifecycle()
    val oemTip = remember { OemGuidance.forManufacturer(Build.MANUFACTURER) }

    // First run opens setup; afterwards Home. Fixed at first composition: later changes use explicit navigation.
    val startDestination = remember { if (settings.onboardingCompleted) Routes.HOME else Routes.ONBOARDING }

    // Permissions can change in system settings while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionsViewModel.refresh() }

    val fixIssue: (HealthIssue) -> Unit = { issue ->
        when (issue) {
            HealthIssue.LOCATION_MISSING, HealthIssue.LOCATION_APPROXIMATE_ONLY -> actions.requestLocation()
            HealthIssue.BACKGROUND_LOCATION_MISSING -> actions.requestBackgroundLocation()
            HealthIssue.NOTIFICATIONS_OFF -> actions.requestNotifications()
            HealthIssue.BATTERY_RESTRICTED -> actions.requestBatteryExemption()
        }
    }

    Column(modifier) {
        if (PermissionHealth.showGpsBanner(snapshot, settings.onboardingCompleted)) {
            GpsBanner(onTurnOn = actions::openLocationSettings)
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f),
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(220)) },
            popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) },
            popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(220)) },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    snapshot = snapshot,
                    selectedTheme = settings.theme,
                    oemTip = oemTip,
                    actions = actions,
                    onThemeSelected = viewModel::setTheme,
                    onFinish = {
                        if (settings.onboardingCompleted) {
                            navController.popBackStack() // re-run from Settings
                        } else {
                            viewModel.setOnboardingCompleted(true)
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    selectedTheme = settings.theme,
                    playback = playback,
                    healthIssues = if (settings.onboardingCompleted) issues else emptyList(),
                    onThemeSelected = viewModel::setTheme,
                    onPlayPause = playbackViewModel::togglePlayPause,
                    onPrevious = playbackViewModel::previous,
                    onNext = playbackViewModel::next,
                    onResumeAuto = playbackViewModel::resumeAuto,
                    onFixIssue = fixIssue,
                    onDismissIssue = permissionsViewModel::dismiss,
                    onOpenAppSettings = actions::openAppSettings,
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenZones = { navController.navigate(Routes.ZONES) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.LIBRARY) {
                val state by manifestViewModel.uiState.collectAsStateWithLifecycle()
                val cachedIds by manifestViewModel.cachedTrackIds.collectAsStateWithLifecycle()
                LibraryScreen(
                    state = state,
                    activeTheme = settings.theme,
                    playback = playback,
                    cachedIds = cachedIds,
                    onPlayTrack = playbackViewModel::playTrack,
                    onOpened = manifestViewModel::refreshCacheStatus,
                    onRefresh = manifestViewModel::refresh,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ZONES) {
                val zonesViewModel: ZonesViewModel = hiltViewModel()
                val rows by zonesViewModel.rows.collectAsStateWithLifecycle()
                val access by zonesViewModel.access.collectAsStateWithLifecycle()
                val status by zonesViewModel.geofenceStatus.collectAsStateWithLifecycle()
                val message by zonesViewModel.message.collectAsStateWithLifecycle()
                val busy by zonesViewModel.busy.collectAsStateWithLifecycle()
                val selectedId by zonesViewModel.selectedId.collectAsStateWithLifecycle()
                val deviceLocation by zonesViewModel.deviceLocation.collectAsStateWithLifecycle()
                val manifestState by manifestViewModel.uiState.collectAsStateWithLifecycle()
                ZonesScreen(
                    rows = rows,
                    manifest = (manifestState as? UiState.Content<LibraryContent>)?.data?.manifest,
                    access = access,
                    status = status,
                    message = message,
                    busy = busy,
                    selectedId = selectedId,
                    deviceLocation = deviceLocation,
                    onAccessChanged = {
                        zonesViewModel.refreshAccess()
                        permissionsViewModel.refresh()
                    },
                    onLoadDeviceLocation = zonesViewModel::loadDeviceLocation,
                    onSelect = zonesViewModel::select,
                    onAddZone = zonesViewModel::addZoneHere,
                    onDropPin = zonesViewModel::addZoneAt,
                    onRadiusChange = zonesViewModel::setRadius,
                    onAssignTrack = zonesViewModel::assignTrack,
                    onRename = zonesViewModel::rename,
                    onEnabledChange = zonesViewModel::setEnabled,
                    onDelete = zonesViewModel::delete,
                    onRetryGeofences = zonesViewModel::refreshGeofences,
                    onDismissMessage = zonesViewModel::dismissMessage,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                val cache by viewModel.audioCache.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    cache = cache,
                    snapshot = snapshot,
                    oemTip = oemTip,
                    actions = actions,
                    onRunSetup = { navController.navigate(Routes.ONBOARDING) },
                    onOpened = viewModel::refreshAudioCache,
                    onVolumeChange = viewModel::setGlobalVolume,
                    onCrossfadeDurationChange = viewModel::setCrossfadeDurationMs,
                    onLinearCurveChange = { linear ->
                        viewModel.setCrossfadeCurve(if (linear) CrossfadeCurve.LINEAR else CrossfadeCurve.EQUAL_POWER)
                    },
                    onWifiOnlyChange = viewModel::setPrecacheWifiOnly,
                    onAudioCacheLimitChange = viewModel::setAudioCacheLimitMb,
                    musicAccessConfigured = musicAccessConfigured,
                    musicAccessError = musicAccessError,
                    onSaveMusicAccessToken = { token -> viewModel.saveMusicAccessToken(token) { manifestViewModel.refresh() } },
                    onClearMusicAccessToken = viewModel::clearMusicAccessToken,
                    onFastZoneDetectionChange = viewModel::setFastZoneDetection,
                    onOpenDebug = if (BuildConfig.DEBUG) ({ navController.navigate(Routes.DEBUG) }) else null,
                    onClearCache = viewModel::clearAudioCache,
                    onHapticsChange = viewModel::setHapticsEnabled,
                    onReduceMotionChange = viewModel::setReduceMotion,
                    onBack = { navController.popBackStack() },
                )
            }
            if (BuildConfig.DEBUG) {
                composable(Routes.DEBUG) {
                    val debugViewModel: DebugViewModel = hiltViewModel()
                    val ui by debugViewModel.ui.collectAsStateWithLifecycle()
                    DebugScreen(
                        ui = ui,
                        buildReport = { debugViewModel.report(it) },
                        onOpened = debugViewModel::refresh,
                        onWeatherOverride = debugViewModel::setWeatherOverride,
                        onTimeOverride = debugViewModel::setTimeOverride,
                        onRefreshWeather = debugViewModel::refreshWeather,
                        onResyncGeofences = debugViewModel::resyncGeofences,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
