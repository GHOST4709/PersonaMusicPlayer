package com.example.personabgm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.domain.model.UiState
import com.example.personabgm.ui.components.ConnectingScreen
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.haptics.rememberHaptics
import com.example.personabgm.ui.navigation.PersonaNavHost
import com.example.personabgm.ui.theme.PersonaBackground
import com.example.personabgm.ui.theme.PersonaTheme
import kotlinx.coroutines.delay

/** How long the manifest may load before the full-screen "Connecting..." state appears. */
private const val LOADING_GRACE_MS = 250L

@Composable
fun PersonaRoot(
    viewModel: AppViewModel = hiltViewModel(),
    // Created here (activity scope) so the manifest fetch starts at app launch, not on first visit to Library.
    manifestViewModel: ManifestViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings

    if (current == null) {
        // First DataStore read in flight: show the splash color instead of a wrong-theme flash.
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0E17)))
    } else {
        PersonaContent(
            settings = current,
            viewModel = viewModel,
            manifestViewModel = manifestViewModel,
        )
    }
}

@Composable
private fun PersonaContent(
    settings: AppSettings,
    viewModel: AppViewModel,
    manifestViewModel: ManifestViewModel,
) {
    val manifestState by manifestViewModel.uiState.collectAsStateWithLifecycle()

    // Loading means "no cached manifest yet". Wait briefly before showing the full-screen state so a
    // warm start (cache hit within a few ms) never flashes it.
    val isLoading = manifestState is UiState.Loading
    val showConnecting by produceState(initialValue = false, key1 = isLoading) {
        value = if (isLoading) {
            delay(LOADING_GRACE_MS)
            true
        } else {
            false
        }
    }

    PersonaTheme(theme = settings.theme, reduceMotionSetting = settings.reduceMotion) {
        val haptics = rememberHaptics(enabled = settings.hapticsEnabled)
        CompositionLocalProvider(LocalHaptics provides haptics) {
            PersonaBackground {
                when {
                    isLoading && showConnecting -> ConnectingScreen()
                    isLoading -> Unit // grace period: background only
                    else -> Scaffold(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) { innerPadding ->
                        PersonaNavHost(
                            settings = settings,
                            viewModel = viewModel,
                            manifestViewModel = manifestViewModel,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}
