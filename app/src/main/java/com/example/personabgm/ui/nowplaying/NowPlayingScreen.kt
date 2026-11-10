package com.example.personabgm.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personabgm.audio.PlaybackMode
import com.example.personabgm.audio.PlaybackUiState
import com.example.personabgm.domain.model.PlaybackScene
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.WeatherType
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.components.PlaybackSceneSwitch
import com.example.personabgm.ui.components.TagChip

@Composable
fun NowPlayingScreen(
    playback: PlaybackUiState,
    scene: PlaybackScene,
    onSceneChange: (PlaybackScene) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResumeAuto: () -> Unit,
    onOpenLibrary: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.track
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PersonaButton(text = "Back", onClick = onBack)
            PersonaHeading(text = "Now Playing", modifier = Modifier.weight(1f))
            PersonaButton(text = "My Music", onClick = onOpenLibrary)
        }
        Box(
            modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
                .background(
                    Brush.linearGradient(
                        listOf(colors.primary.copy(alpha = 0.88f), colors.secondary.copy(alpha = 0.42f), Color(0xFF11131B)),
                    ),
                    RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when (track?.gameTheme?.name) {
                        "PERSONA_3_RELOAD" -> "P3"
                        "PERSONA_4_REVIVAL" -> "P4"
                        "PERSONA_5_ROYAL" -> "P5"
                        else -> "P·BGM"
                    },
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                )
                Text(
                    text = if (scene == PlaybackScene.BATTLE) "BATTLE MODE" else "PERSONA SOUNDTRACK",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.86f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track?.title ?: "Nothing playing", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(track?.artist ?: "Choose a track from My Music", color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            track?.let {
                Text(it.gameTheme.name.replace('_', ' '), color = colors.secondary, style = MaterialTheme.typography.labelLarge)
            }
        }
        playback.context?.let { context ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TagChip(context.zoneName ?: "Roaming")
                TagChip(if (context.time == TimeBucket.DAY) "Day" else "Night")
                TagChip(
                    when (context.weather) {
                        WeatherType.CLEAR -> "Clear"
                        WeatherType.RAIN -> "Rain"
                        WeatherType.SNOW -> "Snow"
                    },
                )
            }
        }
        Text(
            text = when {
                playback.error != null -> playback.error
                playback.isCrossfading -> "Crossfading to the next context match…"
                playback.isBuffering -> "Preparing audio…"
                playback.mode == PlaybackMode.MANUAL -> "Manual selection · automatic context following is paused"
                else -> "Automatic context selection is active"
            },
            color = if (playback.error != null) colors.error else colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        PlaybackSceneSwitch(scene = scene, onSceneChange = onSceneChange)
        PersonaCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonaButton(text = "Previous", onClick = onPrevious, modifier = Modifier.weight(1f))
                PersonaButton(
                    text = if (playback.isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    emphasized = true,
                    modifier = Modifier.weight(1f),
                )
                PersonaButton(text = "Next", onClick = onNext, modifier = Modifier.weight(1f))
            }
            if (playback.mode == PlaybackMode.MANUAL) {
                PersonaButton(text = "Resume Auto", onClick = onResumeAuto, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(
            text = "Lyrics and seek controls will appear when the connected music library exposes that metadata and playback position.",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
