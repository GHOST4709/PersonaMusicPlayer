package com.example.personabgm.ui.scenarios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.model.MusicScenario
import com.example.personabgm.domain.model.ScenarioScope
import com.example.personabgm.domain.model.ScenarioSceneCondition
import com.example.personabgm.domain.model.ScenarioTimeCondition
import com.example.personabgm.domain.model.ScenarioWeatherCondition
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.Zone
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import java.util.UUID

@Composable
fun ScenarioScreen(
    state: ScenarioManagerState,
    onSave: (MusicScenario) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedScope by remember { mutableStateOf(ScenarioScope.ALL) }
    var editing by remember { mutableStateOf<MusicScenario?>(null) }
    var creating by remember { mutableStateOf(false) }

    val scopedScenarios = state.scenarios.filter { it.scope == selectedScope }
    val availableTracks = state.tracks.filter { track ->
        val theme = selectedScope.themeOrNull()
        theme == null || track.gameTheme == theme
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonaButton(text = "Back", onClick = onBack)
            Spacer(Modifier.width(12.dp))
            PersonaHeading(text = "Scenario Rules", modifier = Modifier.weight(1f))
        }
        Text(
            text = "Choose a collection. Each scope has its own rules and New Scenario action.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScenarioScope.entries.forEach { scope ->
                FilterChip(
                    selected = selectedScope == scope,
                    onClick = { selectedScope = scope },
                    label = { Text(scope.label) },
                )
            }
        }
        PersonaButton(
            text = "+  New Scenario",
            onClick = { editing = null; creating = true },
            emphasized = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.manifest == null) {
            Text(
                text = "Your music library has not loaded yet. Load the private library before assigning tracks to a scenario.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (scopedScenarios.isEmpty()) {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                Text("No ${selectedScope.label} scenarios yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Create a rule for time, weather, a configured zone, and Normal or Battle Mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(scopedScenarios.size, key = { scopedScenarios[it].id }) { index ->
                    val scenario = scopedScenarios[index]
                    ScenarioCard(
                        scenario = scenario,
                        tracks = state.tracks,
                        zones = state.zones,
                        onEnabledChange = { onSetEnabled(scenario.id, it) },
                        onEdit = { editing = scenario; creating = true },
                        onDelete = { onDelete(scenario.id) },
                    )
                }
            }
        }
    }

    if (creating) {
        ScenarioEditorDialog(
            initial = editing,
            defaultScope = selectedScope,
            tracks = availableTracks,
            zones = state.zones,
            onDismiss = { creating = false; editing = null },
            onSave = { onSave(it); creating = false; editing = null },
        )
    }
}

@Composable
private fun ScenarioCard(
    scenario: MusicScenario,
    tracks: List<Track>,
    zones: List<Zone>,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val trackCount = scenario.trackIds.count { id -> tracks.any { it.id == id } }
    val zoneName = if (scenario.zoneId == null) "Any location" else zones.firstOrNull { it.id == scenario.zoneId }?.name ?: "Unavailable zone"
    val conditions = listOf(
        scenario.scene.label,
        scenario.time.label,
        scenario.weather.label,
        zoneName,
    ).joinToString("  ·  ")
    PersonaCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(scenario.name, style = MaterialTheme.typography.titleMedium)
                Text(conditions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$trackCount assigned track${if (trackCount == 1) "" else "s"}  ·  Priority ${scenario.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Switch(checked = scenario.enabled, onCheckedChange = onEnabledChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ScenarioEditorDialog(
    initial: MusicScenario?,
    defaultScope: ScenarioScope,
    tracks: List<Track>,
    zones: List<Zone>,
    onDismiss: () -> Unit,
    onSave: (MusicScenario) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var scope by remember(initial) { mutableStateOf(initial?.scope ?: defaultScope) }
    var scene by remember(initial) { mutableStateOf(initial?.scene ?: ScenarioSceneCondition.ANY) }
    var time by remember(initial) { mutableStateOf(initial?.time ?: ScenarioTimeCondition.ANY) }
    var weather by remember(initial) { mutableStateOf(initial?.weather ?: ScenarioWeatherCondition.ANY) }
    var zoneId by remember(initial) { mutableStateOf(initial?.zoneId) }
    var priority by remember(initial) { mutableStateOf(initial?.priority?.toString() ?: "0") }
    val selectedTracks = remember(initial) { mutableStateOf(initial?.trackIds?.toSet().orEmpty()) }

    val tracksForScope = tracks.filter { track -> scope.themeOrNull()?.let { it == track.gameTheme } ?: true }
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Scenario" else "Edit Scenario") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(520.dp).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text("Scenario name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                ChipChoices(ScenarioScope.entries, scope, { scope = it }) { it.label }
                Text("Playback scene", style = MaterialTheme.typography.labelLarge)
                ChipChoices(ScenarioSceneCondition.entries, scene, { scene = it }) { it.label }
                Text("Time", style = MaterialTheme.typography.labelLarge)
                ChipChoices(ScenarioTimeCondition.entries, time, { time = it }) { it.label }
                Text("Weather", style = MaterialTheme.typography.labelLarge)
                ChipChoices(ScenarioWeatherCondition.entries, weather, { weather = it }) { it.label }
                Text("Location", style = MaterialTheme.typography.labelLarge)
                var zoneMenuOpen by remember { mutableStateOf(false) }
                Column {
                    PersonaButton(
                        text = zoneId?.let { id -> zones.firstOrNull { it.id == id }?.name } ?: "Any configured zone",
                        onClick = { zoneMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.DropdownMenu(expanded = zoneMenuOpen, onDismissRequest = { zoneMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Any configured zone") }, onClick = { zoneId = null; zoneMenuOpen = false })
                        zones.forEach { zone ->
                            DropdownMenuItem(text = { Text(zone.name) }, onClick = { zoneId = zone.id; zoneMenuOpen = false })
                        }
                    }
                }
                Text("Assigned tracks", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Select tracks from the loaded manifest. The rule only uses tracks that belong to the active Persona category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tracksForScope.isEmpty()) {
                    Text("No matching tracks are available in the current manifest.", color = MaterialTheme.colorScheme.error)
                }
                tracksForScope.forEach { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedTracks.value = if (track.id in selectedTracks.value) selectedTracks.value - track.id else selectedTracks.value + track.id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = track.id in selectedTracks.value,
                            onCheckedChange = { checked ->
                                selectedTracks.value = if (checked) selectedTracks.value + track.id else selectedTracks.value - track.id
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyMedium)
                            Text("${track.artist} · ${track.gameTheme.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter(Char::isDigit).take(3) },
                    label = { Text("Priority (0–999)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && tracksForScope.any { it.id in selectedTracks.value },
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        MusicScenario(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            scope = scope,
                            enabled = initial?.enabled ?: true,
                            scene = scene,
                            time = time,
                            weather = weather,
                            zoneId = zoneId,
                            trackIds = tracksForScope.map { it.id }.filter { it in selectedTracks.value },
                            priority = priority.toIntOrNull()?.coerceIn(0, 999) ?: 0,
                            createdAt = initial?.createdAt ?: now,
                            updatedAt = now,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun <T> ChipChoices(
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { value ->
            FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) })
        }
    }
}
