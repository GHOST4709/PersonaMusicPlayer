package com.example.personabgm.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.WeatherType
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.components.headingSemantics
import com.example.personabgm.ui.formatAge
import com.example.personabgm.ui.formatBytes
import com.example.personabgm.ui.haptics.LocalHaptics

/** Debug builds only. Everything here is read-only except the two override rows and the three actions. */
@Composable
fun DebugScreen(
    ui: DebugViewModel.DebugUi?,
    buildReport: (DebugViewModel.DebugUi) -> String,
    onOpened: () -> Unit,
    onWeatherOverride: (WeatherType?) -> Unit,
    onTimeOverride: (TimeBucket?) -> Unit,
    onRefreshWeather: () -> Unit,
    onResyncGeofences: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    LaunchedEffect(Unit) { onOpened() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PersonaHeading(text = "Debug") }

        if (ui == null) {
            item { Text("Loading...", style = MaterialTheme.typography.bodyMedium, color = colors.onBackground) }
        } else {
            val now = System.currentTimeMillis()

            item {
                Section("Test overrides") {
                    Text("Pretend the weather is", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    ChoiceRow(
                        options = listOf<WeatherType?>(null) + WeatherType.entries,
                        selected = ui.weatherOverride,
                        label = { it?.name?.lowercase()?.replaceFirstChar(Char::titlecase) ?: "Real" },
                        onSelect = onWeatherOverride,
                    )
                    Text(
                        text = "Pretend the time is",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ChoiceRow(
                        options = listOf<TimeBucket?>(null) + TimeBucket.entries,
                        selected = ui.timeOverride,
                        label = { it?.name?.lowercase()?.replaceFirstChar(Char::titlecase) ?: "Real" },
                        onSelect = onTimeOverride,
                    )
                    Text(
                        text = "Weather and time overrides only take effect while the weather override setting is on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                val p = ui.playback
                val c = p.context
                Section("Now") {
                    Mono("track: ${p.track?.id ?: "none"}")
                    Mono("playing=${p.isPlaying} crossfading=${p.isCrossfading} mode=${p.mode}")
                    Mono("zone: ${c?.zoneName ?: "roaming"}  weather: ${c?.weather ?: "-"}  time: ${c?.time ?: "-"}")
                    if (p.error != null) Mono("error: ${p.error}")
                }
            }

            item {
                val stateById = ui.zoneStates.associateBy { it.zoneId }
                Section("Zones") {
                    if (ui.zones.isEmpty()) Mono("none")
                    ui.zones.forEach { z ->
                        val s = stateById[z.id]
                        Mono("${z.name}  r=${z.radiusM.toInt()}m  ${if (z.enabled) "on" else "off"}")
                        Mono("  ${s?.let { "${it.state} ${formatAge(now - it.sinceEpochMs)}" } ?: "no state yet"}")
                    }
                    Mono("geofences: ${ui.geofence}")
                }
            }

            item {
                Section("Weather") {
                    Mono(ui.weather?.let { "${it.type} ${formatAge(now - it.fetchedAtMs)} at ${it.lat}, ${it.lng}" } ?: "no reading yet")
                }
            }

            item {
                Section("Storage") {
                    Mono("audio cache: ${ui.cache.cachedTrackIds.size} tracks, ${formatBytes(ui.cache.totalBytes)}")
                    Mono(
                        ui.manifestMeta?.let { "manifest v${it.version}, ${formatAge(now - it.fetchedAt)}, etag ${it.etag ?: "none"}" }
                            ?: "manifest: nothing cached",
                    )
                }
            }

            item {
                val perms = ui.permissions
                Section("Permissions") {
                    Mono("precise=${perms.fineLocation} approx=${perms.coarseLocation} background=${perms.backgroundLocation}")
                    Mono("notifications=${perms.notifications} battery=${perms.batteryExempt} bluetooth=${perms.bluetoothConnect}")
                    Mono("location services on=${perms.locationServicesOn}")
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonaButton("Refresh weather", onRefreshWeather, modifier = Modifier.weight(1f))
                    PersonaButton("Re-register geofences", onResyncGeofences, modifier = Modifier.weight(1f))
                }
            }

            item {
                PersonaButton(
                    text = "Copy report",
                    onClick = {
                        haptics.emphasize()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Persona BGM debug report", buildReport(ui)))
                    },
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { PersonaButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    PersonaCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.headingSemantics().padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun Mono(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.secondary,
                    selectedLabelColor = colors.onSecondary,
                ),
            )
        }
    }
}
