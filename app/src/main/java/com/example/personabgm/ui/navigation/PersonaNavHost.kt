package com.example.personabgm.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.domain.model.CrossfadeCurve
import com.example.personabgm.domain.model.UiState
import com.example.personabgm.ui.AppViewModel
import com.example.personabgm.ui.LibraryContent
import com.example.personabgm.ui.ManifestViewModel
import com.example.personabgm.ui.PlaybackViewModel
import com.example.personabgm.ui.home.HomeScreen
import com.example.personabgm.ui.library.LibraryScreen
import com.example.personabgm.ui.settings.SettingsScreen
import com.example.personabgm.ui.zones.ZonesScreen
import com.example.personabgm.ui.zones.ZonesViewModel
import com.example.personabgm.ui.theme.LocalReduceMotion

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ZONES = "zones"
    const val SETTINGS = "settings"
}

@Composable
fun PersonaNavHost(
    settings: AppSettings,
    viewModel: AppViewModel,
    manifestViewModel: ManifestViewModel,
    modifier: Modifier = Modifier,
    // Activity-scoped here, so every screen shares one adapter over the singleton PlaybackRemote.
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val reduceMotion = LocalReduceMotion.current
    val playback by playbackViewModel.state.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) },
        exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(220)) },
        popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) },
        popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(220)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                selectedTheme = settings.theme,
                playback = playback,
                onThemeSelected = viewModel::setTheme,
                onPlayPause = playbackViewModel::togglePlayPause,
                onPrevious = playbackViewModel::previous,
                onNext = playbackViewModel::next,
                onResumeAuto = playbackViewModel::resumeAuto,
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
            val manifestState by manifestViewModel.uiState.collectAsStateWithLifecycle()
            ZonesScreen(
                rows = rows,
                manifest = (manifestState as? UiState.Content<LibraryContent>)?.data?.manifest,
                access = access,
                status = status,
                message = message,
                busy = busy,
                onAccessChanged = zonesViewModel::refreshAccess,
                onAddZone = zonesViewModel::addZoneHere,
                onRadiusChange = zonesViewModel::setRadius,
                onAssignTrack = zonesViewModel::assignTrack,
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
                onOpened = viewModel::refreshAudioCache,
                onVolumeChange = viewModel::setGlobalVolume,
                onCrossfadeDurationChange = viewModel::setCrossfadeDurationMs,
                onLinearCurveChange = { linear ->
                    viewModel.setCrossfadeCurve(if (linear) CrossfadeCurve.LINEAR else CrossfadeCurve.EQUAL_POWER)
                },
                onWifiOnlyChange = viewModel::setPrecacheWifiOnly,
                onClearCache = viewModel::clearAudioCache,
                onHapticsChange = viewModel::setHapticsEnabled,
                onReduceMotionChange = viewModel::setReduceMotion,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
