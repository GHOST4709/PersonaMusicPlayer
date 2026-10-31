package com.example.personabgm.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.personabgm.audio.PlaybackMode
import com.example.personabgm.audio.PlaybackUiState
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.theme.LocalPersonaStyle
import com.example.personabgm.ui.theme.PersonaPalette
import com.example.personabgm.ui.theme.PersonaStyle
import com.example.personabgm.ui.theme.PersonaStyles

@Composable
fun HomeScreen(
    selectedTheme: GameTheme,
    playback: PlaybackUiState,
    onThemeSelected: (GameTheme) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResumeAuto: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val colors = MaterialTheme.colorScheme

    // Until onboarding (Phase 5) exists, ask for notification permission the first time Play is pressed
    // so the lock-screen / notification controls show up on Android 13+. Playback works either way.
    val context = LocalContext.current
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val playPauseWithPermission: () -> Unit = {
        if (!playback.isPlaying &&
            !askedForNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onPlayPause()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PersonaHeading(text = "Now playing") }

        item {
            NowPlayingCard(
                state = playback,
                onPlayPause = playPauseWithPermission,
                onPrevious = onPrevious,
                onNext = onNext,
                onResumeAuto = onResumeAuto,
            )
        }

        item {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        items(items = PersonaStyles.all, key = { it.theme.name }) { style ->
            ThemeOptionCard(
                style = style,
                selected = style.theme == selectedTheme,
                onClick = {
                    if (style.theme != selectedTheme) haptics.emphasize()
                    onThemeSelected(style.theme)
                },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonaButton(
                    text = "Library",
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                PersonaButton(
                    text = "Settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One sentence about what is happening, or null when there is nothing worth saying. */
private fun statusLine(state: PlaybackUiState): String = when {
    state.error != null -> state.error
    state.isCrossfading -> "Crossfading..."
    state.isBuffering && state.isPlaying -> "Buffering..."
    state.track == null -> "Press Play to start the music for your theme."
    state.mode == PlaybackMode.MANUAL -> "Manual pick. Following your theme is paused."
    else -> "Following your theme."
}

@Composable
private fun NowPlayingCard(
    state: PlaybackUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResumeAuto: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val track = state.track

    PersonaCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = track?.title ?: "Nothing playing yet",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        if (track != null) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            text = statusLine(state),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.error != null) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        PersonaButton(
            text = if (state.isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            emphasized = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            PersonaButton(text = "Previous", onClick = onPrevious, modifier = Modifier.weight(1f))
            PersonaButton(text = "Next", onClick = onNext, modifier = Modifier.weight(1f))
        }
        if (state.mode == PlaybackMode.MANUAL) {
            PersonaButton(
                text = "Resume auto",
                onClick = onResumeAuto,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ThemeOptionCard(
    style: PersonaStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    PersonaCard(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Swatches(palette = style.palette)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = style.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (selected) ActiveBadge()
        }
    }
}

/** Decorative color chips (primary, accent, background); hidden from screen readers. */
@Composable
private fun Swatches(palette: PersonaPalette) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        listOf(palette.primary, palette.accent, palette.background).forEach { color ->
            Swatch(color = color, outline = palette.outline)
        }
    }
}

@Composable
private fun Swatch(color: Color, outline: Color) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, outline, CircleShape),
    )
}

@Composable
private fun ActiveBadge() {
    val colors = MaterialTheme.colorScheme
    val shape = LocalPersonaStyle.current.shapes.badge
    Box(
        Modifier
            .background(colors.secondary, shape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Active",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSecondary,
        )
    }
}
