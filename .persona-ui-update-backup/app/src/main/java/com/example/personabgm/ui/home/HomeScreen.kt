package com.example.personabgm.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personabgm.audio.PlaybackMode
import com.example.personabgm.audio.PlaybackUiState
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.WeatherType
import com.example.personabgm.domain.permissions.HealthIssue
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.components.TagChip
import com.example.personabgm.ui.components.headingSemantics
import com.example.personabgm.ui.components.politeLiveRegion
import com.example.personabgm.ui.components.ThemeOptionCard
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.permissions.PermissionHealthCard
import com.example.personabgm.ui.theme.PersonaStyles

@Composable
fun HomeScreen(
    selectedTheme: GameTheme,
    playback: PlaybackUiState,
    healthIssues: List<HealthIssue>,
    onThemeSelected: (GameTheme) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResumeAuto: () -> Unit,
    onFixIssue: (HealthIssue) -> Unit,
    onDismissIssue: (HealthIssue) -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PersonaHeading(text = "Now playing") }

        if (healthIssues.isNotEmpty()) {
            item {
                PermissionHealthCard(
                    issues = healthIssues,
                    onFix = onFixIssue,
                    onDismiss = onDismissIssue,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
        }

        item {
            NowPlayingCard(
                state = playback,
                onPlayPause = onPlayPause,
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
                modifier = Modifier.headingSemantics().padding(top = 8.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonaButton(text = "Library", onClick = onOpenLibrary, modifier = Modifier.weight(1f))
                    PersonaButton(text = "Zones", onClick = onOpenZones, modifier = Modifier.weight(1f))
                }
                PersonaButton(text = "Settings", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun weatherLabel(weather: WeatherType) = when (weather) {
    WeatherType.CLEAR -> "Clear"
    WeatherType.RAIN -> "Rain"
    WeatherType.SNOW -> "Snow"
}

private fun timeLabel(time: TimeBucket) = when (time) {
    TimeBucket.DAY -> "Day"
    TimeBucket.NIGHT -> "Night"
}

/** One sentence about what is happening. */
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
            // Announce "Crossfading..." and errors as they appear.
            modifier = Modifier.politeLiveRegion().padding(top = 4.dp, bottom = 8.dp),
        )
        state.context?.let { info ->
            // Why AUTO is playing what it is: where you are, the weather, and the time of day.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                TagChip(text = info.zoneName ?: "Roaming", modifier = Modifier.weight(1f, fill = false))
                TagChip(text = weatherLabel(info.weather))
                TagChip(text = timeLabel(info.time))
            }
        }

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
