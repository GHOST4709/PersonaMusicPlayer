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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.haptics.rememberHaptics
import com.example.personabgm.ui.navigation.PersonaNavHost
import com.example.personabgm.ui.theme.PersonaBackground
import com.example.personabgm.ui.theme.PersonaTheme

@Composable
fun PersonaRoot(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings

    if (current == null) {
        // First DataStore read in flight: show the splash color instead of a wrong-theme flash.
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0E17)))
    } else {
        PersonaTheme(theme = current.theme, reduceMotionSetting = current.reduceMotion) {
            val haptics = rememberHaptics(enabled = current.hapticsEnabled)
            CompositionLocalProvider(LocalHaptics provides haptics) {
                PersonaBackground {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) { innerPadding ->
                        PersonaNavHost(
                            settings = current,
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}
