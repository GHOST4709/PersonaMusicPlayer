package com.example.personabgm.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.ui.AppViewModel
import com.example.personabgm.ui.ManifestViewModel
import com.example.personabgm.ui.home.HomeScreen
import com.example.personabgm.ui.library.LibraryScreen
import com.example.personabgm.ui.settings.SettingsScreen
import com.example.personabgm.ui.theme.LocalReduceMotion

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

@Composable
fun PersonaNavHost(
    settings: AppSettings,
    viewModel: AppViewModel,
    manifestViewModel: ManifestViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val reduceMotion = LocalReduceMotion.current

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
                onThemeSelected = viewModel::setTheme,
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LIBRARY) {
            val state by manifestViewModel.uiState.collectAsStateWithLifecycle()
            LibraryScreen(
                state = state,
                activeTheme = settings.theme,
                onRefresh = manifestViewModel::refresh,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onHapticsChange = viewModel::setHapticsEnabled,
                onReduceMotionChange = viewModel::setReduceMotion,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
