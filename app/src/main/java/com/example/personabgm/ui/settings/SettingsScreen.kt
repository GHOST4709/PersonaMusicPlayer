package com.example.personabgm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.personabgm.audio.CacheSnapshot
import com.example.personabgm.audio.MediaCache
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.domain.model.CrossfadeCurve
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.formatBytes
import com.example.personabgm.ui.haptics.LocalHaptics
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    cache: CacheSnapshot,
    onOpened: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCrossfadeDurationChange: (Int) -> Unit,
    onLinearCurveChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { onOpened() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PersonaHeading(text = "Settings") }

        item { SectionTitle("Playback") }
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                SliderRow(
                    title = "Volume",
                    value = settings.globalVolume,
                    range = 0f..1f,
                    steps = 0,
                    format = { "${(it * 100).roundToInt()}%" },
                    onCommit = onVolumeChange,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                SliderRow(
                    title = "Crossfade length",
                    value = settings.crossfadeDurationMs / 1000f,
                    range = 0.5f..8f,
                    steps = 14, // half-second steps
                    format = { String.format(Locale.US, "%.1f s", it) },
                    onCommit = { onCrossfadeDurationChange((it * 1000).roundToInt()) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                SwitchRow(
                    title = "Linear crossfade",
                    description = "A straight-line fade instead of the default equal-power curve. " +
                        "Equal-power avoids a dip in loudness halfway through.",
                    checked = settings.crossfadeCurve == CrossfadeCurve.LINEAR,
                    onCheckedChange = onLinearCurveChange,
                )
            }
        }

        item { SectionTitle("Audio cache") }
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Download on Wi-Fi only",
                    description = "Pre-downloads your themes' tracks so they start instantly, even offline.",
                    checked = settings.precacheWifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                Text(
                    text = "Saved audio: ${formatBytes(cache.totalBytes)} of ${formatBytes(MediaCache.MAX_BYTES)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                PersonaButton(
                    text = "Clear audio cache",
                    onClick = onClearCache,
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { SectionTitle("Interface") }
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Haptic feedback",
                    description = "Short vibrations when you navigate, switch themes, or drop a pin.",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = onHapticsChange,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                SwitchRow(
                    title = "Reduce motion",
                    description = "Stops background animation and screen transitions. The system " +
                        "\"Remove animations\" setting is also respected.",
                    checked = settings.reduceMotion,
                    onCheckedChange = onReduceMotionChange,
                )
            }
        }

        item {
            PersonaButton(
                text = "Back",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.tick()
                    onCheckedChange(it)
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        // The Row handles the toggle, so the Switch itself is not separately clickable.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Slider that shows the live value while dragging but only saves when the finger lifts. */
@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var dragValue by remember(value) { mutableFloatStateOf(value) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(text = format(dragValue), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onCommit(dragValue) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}
