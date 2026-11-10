package com.example.personabgm.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personabgm.audio.PlaybackUiState
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.PlaybackScene
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import com.example.personabgm.domain.model.UiState
import com.example.personabgm.ui.LibraryContent
import com.example.personabgm.ui.components.ConnectingScreen
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.components.PlaybackSceneSwitch
import com.example.personabgm.ui.components.StatusBadge
import com.example.personabgm.ui.components.TagChip
import com.example.personabgm.ui.components.headingSemantics
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.theme.PersonaStyles

/**
 * Track list from the manifest, grouped by theme with the active theme first (spec 3.3).
 * Tap a track to play it (this enters Manual Override); "Saved" means it is fully on disk.
 */
@Composable
fun LibraryScreen(
    state: UiState<LibraryContent>,
    activeTheme: GameTheme,
    playbackScene: PlaybackScene,
    onPlaybackSceneChange: (PlaybackScene) -> Unit,
    playback: PlaybackUiState,
    cachedIds: Set<String>,
    onPlayTrack: (Track) -> Unit,
    onOpened: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Cache status can change while playing, so re-check whenever the screen opens.
    LaunchedEffect(Unit) { onOpened() }

    when (state) {
        UiState.Loading -> ConnectingScreen(modifier)
        is UiState.Error -> LibraryError(
            message = state.message,
            onRetry = state.retry ?: onRefresh,
            onBack = onBack,
            modifier = modifier,
        )
        is UiState.Content -> LibraryList(
            content = state.data,
            activeTheme = activeTheme,
            playbackScene = playbackScene,
            onPlaybackSceneChange = onPlaybackSceneChange,
            playback = playback,
            cachedIds = cachedIds,
            onPlayTrack = onPlayTrack,
            onRefresh = onRefresh,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        PersonaCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Couldn't load the track list",
                style = MaterialTheme.typography.titleMedium,
                color = colors.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonaButton(text = "Try again", onClick = onRetry, modifier = Modifier.weight(1f))
            PersonaButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LibraryList(
    content: LibraryContent,
    activeTheme: GameTheme,
    playbackScene: PlaybackScene,
    onPlaybackSceneChange: (PlaybackScene) -> Unit,
    playback: PlaybackUiState,
    cachedIds: Set<String>,
    onPlayTrack: (Track) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val manifest = content.manifest
    var selectedFilter by remember { mutableStateOf<GameTheme?>(null) }
    val styles = PersonaStyles.all
        .filter { selectedFilter == null || it.theme == selectedFilter }
        .sortedByDescending { it.theme == activeTheme }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PersonaHeading(text = "My Music") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val filters = listOf(
                    "All" to null,
                    "Persona 3 Reloaded" to GameTheme.PERSONA_3_RELOAD,
                    "Persona 4 Revival" to GameTheme.PERSONA_4_REVIVAL,
                    "Persona 5 Royal" to GameTheme.PERSONA_5_ROYAL,
                )
                filters.forEach { (label, theme) ->
                    FilterChip(
                        selected = selectedFilter == theme,
                        onClick = { selectedFilter = theme },
                        label = { Text(label) },
                    )
                }
            }
        }
        item { PlaybackSceneSwitch(scene = playbackScene, onSceneChange = onPlaybackSceneChange) }

        if (content.statusMessage != null) {
            item {
                Text(
                    text = if (content.offline) "Offline. Showing your saved track list. ${content.statusMessage}" else content.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (content.offline) colors.secondary else colors.error,
                )
            }
        }

        for (style in styles) {
            val tracks = manifest.tracksFor(style.theme)
            if (tracks.isEmpty()) continue

            item(key = "theme-header:${style.theme.name}") {
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                    modifier = Modifier.headingSemantics().padding(top = 12.dp),
                )
            }
            items(items = tracks, key = { it.id }) { track ->
                TrackCard(
                    track = track,
                    isRoamingDefault = isDefault(manifest, track),
                    isCurrent = playback.track?.id == track.id,
                    isPlaying = playback.isPlaying,
                    isSaved = track.id in cachedIds,
                    onClick = {
                        haptics.tick()
                        onPlayTrack(track)
                    },
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                PersonaButton(
                    text = if (content.isRefreshing) "Refreshing..." else "Refresh",
                    onClick = onRefresh,
                    enabled = !content.isRefreshing,
                    modifier = Modifier.weight(1f),
                )
                PersonaButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun isDefault(manifest: Manifest, track: Track) = manifest.themeDefaults[track.gameTheme] == track.id

@Composable
private fun TrackCard(
    track: Track,
    isRoamingDefault: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSaved: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    PersonaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = "Play ${track.title}",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            // One badge at a time: what is happening now beats the static "roaming default" label.
            when {
                isCurrent -> StatusBadge(if (isPlaying) "Playing" else "Paused")
                isRoamingDefault -> StatusBadge("Roaming default")
            }
        }
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            conditionLabels(track).forEach { TagChip(it) }
            if (isSaved) TagChip("Saved")
        }
    }
}

/** Human labels for when a track plays; "Any time" when it has no time or weather restriction. */
private fun conditionLabels(track: Track): List<String> {
    val labels = buildList {
        when (track.timeOfDay) {
            TrackTime.DAY -> add("Day")
            TrackTime.NIGHT -> add("Night")
            TrackTime.ALL -> Unit
        }
        when (track.weather) {
            TrackWeather.CLEAR -> add("Clear")
            TrackWeather.RAIN -> add("Rain")
            TrackWeather.SNOW -> add("Snow")
            TrackWeather.ALL -> Unit
        }
    }
    return labels.ifEmpty { listOf("Any time") }
}
