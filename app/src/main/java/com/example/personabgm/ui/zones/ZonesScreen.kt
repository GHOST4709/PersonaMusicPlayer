package com.example.personabgm.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.personabgm.BuildConfig
import com.example.personabgm.domain.model.LatLng
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.Zone
import com.example.personabgm.domain.model.ZoneRules
import com.example.personabgm.geofence.GeofenceStatus
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.components.PersonaSliderRow
import com.example.personabgm.ui.components.PersonaSwitchRow
import com.example.personabgm.ui.components.StatusBadge
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.permissions.PermissionActions
import com.example.personabgm.ui.permissions.rememberPermissionActions
import com.example.personabgm.ui.theme.PersonaStyles
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Zone management with a map (spec 3.1 to 3.3). The map sits above a scrolling list so that panning the map
 * never fights list scrolling. Press and hold the map to drop a zone; tap a circle or a card to select one.
 */
@Composable
fun ZonesScreen(
    rows: List<ZoneRow>,
    manifest: Manifest?,
    access: LocationAccess,
    status: GeofenceStatus,
    message: String?,
    busy: Boolean,
    selectedId: String?,
    deviceLocation: LatLng?,
    onAccessChanged: () -> Unit,
    onLoadDeviceLocation: () -> Unit,
    onSelect: (String?) -> Unit,
    onAddZone: () -> Unit,
    onDropPin: (lat: Double, lng: Double) -> Unit,
    onRadiusChange: (String, Float) -> Unit,
    onAssignTrack: (String, String?) -> Unit,
    onRename: (String, String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onRetryGeofences: () -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val actions = rememberPermissionActions(onResult = onAccessChanged)

    // The person may come back from system settings having changed the permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onAccessChanged() }
    LaunchedEffect(access.fine) { if (access.fine) onLoadDeviceLocation() }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_VISIBLE_MS)
            onDismissMessage()
        }
    }

    var choosingMusicFor by remember { mutableStateOf<Zone?>(null) }
    var renaming by remember { mutableStateOf<Zone?>(null) }
    var confirmingDelete by remember { mutableStateOf<Zone?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PersonaHeading(text = "Zones")
            if (BuildConfig.HAS_MAPS_API_KEY) {
                ZoneMap(
                    rows = rows,
                    selectedId = selectedId,
                    deviceLocation = deviceLocation,
                    myLocationEnabled = access.fine,
                    onSelect = { onSelect(it) },
                    onDropPin = { lat, lng ->
                        haptics.emphasize()
                        onDropPin(lat, lng)
                    },
                )
                Text(
                    text = "Press and hold the map to drop a zone there.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground,
                )
            } else {
                MapKeyMissingCard()
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { LocationAccessCard(access = access, actions = actions) }

            item { GeofenceStatusLine(status = status, zoneCount = rows.size, onRetry = onRetryGeofences) }

            item {
                PersonaButton(
                    text = if (busy) "Finding you..." else "Add zone here",
                    onClick = onAddZone,
                    enabled = access.fine && !busy,
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (message != null) {
                item { Text(text = message, style = MaterialTheme.typography.bodyMedium, color = colors.secondary) }
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        text = "No zones yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            items(items = rows, key = { it.zone.id }) { row ->
                ZoneCard(
                    row = row,
                    selected = row.zone.id == selectedId,
                    musicLabel = musicLabel(row.zone, manifest),
                    onSelect = { onSelect(row.zone.id) },
                    onRadiusChange = { onRadiusChange(row.zone.id, it) },
                    onChangeMusic = { choosingMusicFor = row.zone },
                    onRename = { renaming = row.zone },
                    onEnabledChange = { onEnabledChange(row.zone.id, it) },
                    onDelete = { confirmingDelete = row.zone },
                )
            }

            item { PersonaButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth()) }
        }
    }

    choosingMusicFor?.let { zone ->
        TrackPickerDialog(
            manifest = manifest,
            selectedId = zone.assignedTrackId,
            onSelect = { trackId ->
                onAssignTrack(zone.id, trackId)
                choosingMusicFor = null
            },
            onDismiss = { choosingMusicFor = null },
        )
    }

    renaming?.let { zone ->
        RenameDialog(
            currentName = zone.name,
            onConfirm = { name ->
                onRename(zone.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    confirmingDelete?.let { zone ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("Delete ${zone.name}?") },
            text = { Text("The zone stops playing music here. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(zone.id)
                        confirmingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmingDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary),
                ) { Text("Cancel") }
            },
        )
    }
}

private fun musicLabel(zone: Zone, manifest: Manifest?): String {
    val assignedId = zone.assignedTrackId ?: return "your theme's default"
    return manifest?.trackById(assignedId)?.title ?: "your theme's default (the chosen track is no longer listed)"
}

/** Shown instead of the map when the build has no Google Maps API key. Everything else still works. */
@Composable
private fun MapKeyMissingCard() {
    val colors = MaterialTheme.colorScheme
    PersonaCard(modifier = Modifier.fillMaxWidth()) {
        Text("The map isn't set up", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(
            text = "Add MAPS_API_KEY=your-key to local.properties and rebuild to see zones on a map. " +
                "You can still add and manage zones with the buttons below.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LocationAccessCard(access: LocationAccess, actions: PermissionActions) {
    val colors = MaterialTheme.colorScheme
    PersonaCard(modifier = Modifier.fillMaxWidth()) {
        when {
            !access.fine -> {
                Text("Location is off for this app", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    text = "Zones need your precise location to know when you arrive and leave.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                PersonaButton("Allow location", actions::requestLocation, modifier = Modifier.fillMaxWidth())
            }

            !access.background -> {
                Text("One more step", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    text = "To change the music while the app is closed, choose \"Allow all the time\" for location. " +
                        "Until then zones only work while the app is open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                PersonaButton("Open location settings", actions::requestBackgroundLocation, modifier = Modifier.fillMaxWidth())
            }

            else -> {
                Text("Location is ready", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    text = "Zones will switch your music even when the app is closed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GeofenceStatusLine(status: GeofenceStatus, zoneCount: Int, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        GeofenceStatus.Idle, GeofenceStatus.MissingPermission -> Unit
        is GeofenceStatus.Registered -> Text(
            text = if (zoneCount == 0) {
                "Add your first zone below, or press and hold the map."
            } else {
                "Watching ${status.count} active zone${if (status.count == 1) "" else "s"}."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onBackground,
        )

        is GeofenceStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Zone watching hit a problem: ${status.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
            )
            PersonaButton("Try again", onRetry)
        }
    }
}

@Composable
private fun ZoneCard(
    row: ZoneRow,
    selected: Boolean,
    musicLabel: String,
    onSelect: () -> Unit,
    onRadiusChange: (Float) -> Unit,
    onChangeMusic: () -> Unit,
    onRename: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val zone = row.zone

    PersonaCard(modifier = Modifier.fillMaxWidth(), selected = selected, onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = zone.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (row.inside && zone.enabled) StatusBadge("Inside")
        }
        Text(
            text = "Plays $musicLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        PersonaSliderRow(
            title = "Radius",
            value = zone.radiusM,
            range = ZoneRules.MIN_RADIUS_M..ZoneRules.MAX_RADIUS_M,
            steps = 7, // 50 m steps
            format = { "${it.roundToInt()} m" },
            onCommit = onRadiusChange,
        )
        PersonaSwitchRow(
            title = "Active",
            description = null,
            checked = zone.enabled,
            onCheckedChange = onEnabledChange,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonaButton("Change music", onChangeMusic, modifier = Modifier.weight(1f))
                PersonaButton("Rename", onRename, modifier = Modifier.weight(1f))
            }
            PersonaButton("Delete", onDelete, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename zone") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text("Zone name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.secondary,
                    focusedLabelColor = colors.secondary,
                    cursorColor = colors.secondary,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TrackPickerDialog(
    manifest: Manifest?,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Music for this zone") },
        text = {
            LazyColumn {
                item {
                    PickerRow(
                        title = "Theme default",
                        subtitle = "Plays your theme's usual track",
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(items = manifest?.tracks.orEmpty(), key = { it.id }) { track ->
                    PickerRow(
                        title = track.title,
                        subtitle = "${track.artist} \u00B7 ${PersonaStyles.of(track.gameTheme).displayName}",
                        selected = track.id == selectedId,
                        onClick = { onSelect(track.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PickerRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The row handles the click, so the radio button itself is not separately clickable.
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.secondary),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

private const val MESSAGE_VISIBLE_MS = 4_000L
private const val MAX_NAME_LENGTH = 40
