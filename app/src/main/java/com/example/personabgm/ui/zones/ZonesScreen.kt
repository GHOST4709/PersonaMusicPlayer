package com.example.personabgm.ui.zones

import android.Manifest as AndroidManifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.example.personabgm.ui.theme.PersonaStyles
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Zone management (spec 3.3). The map-based editor arrives in a later build; this screen is enough to
 * create a zone where you stand, give it music, and watch it switch on and off.
 */
@Composable
fun ZonesScreen(
    rows: List<ZoneRow>,
    manifest: Manifest?,
    access: LocationAccess,
    status: GeofenceStatus,
    message: String?,
    busy: Boolean,
    onAccessChanged: () -> Unit,
    onAddZone: () -> Unit,
    onRadiusChange: (String, Float) -> Unit,
    onAssignTrack: (String, String?) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onRetryGeofences: () -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onAccessChanged()
    }
    // The person may come back from system settings having changed the permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onAccessChanged() }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_VISIBLE_MS)
            onDismissMessage()
        }
    }

    var choosingMusicFor by remember { mutableStateOf<Zone?>(null) }
    var confirmingDelete by remember { mutableStateOf<Zone?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PersonaHeading(text = "Zones") }

        item {
            Text(
                text = "A zone plays its own music while you are inside it. Stand where you want one and add it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
            )
        }

        item {
            LocationAccessCard(
                access = access,
                onAllowLocation = {
                    permissionLauncher.launch(
                        arrayOf(AndroidManifest.permission.ACCESS_FINE_LOCATION, AndroidManifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
        }

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
            item {
                Text(text = message, style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
            }
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
                musicLabel = musicLabel(row.zone, manifest),
                onRadiusChange = { onRadiusChange(row.zone.id, it) },
                onChangeMusic = { choosingMusicFor = row.zone },
                onEnabledChange = { onEnabledChange(row.zone.id, it) },
                onDelete = { confirmingDelete = row.zone },
            )
        }

        item { PersonaButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth()) }
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

@Composable
private fun LocationAccessCard(
    access: LocationAccess,
    onAllowLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
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
                PersonaButton("Allow location", onAllowLocation, modifier = Modifier.fillMaxWidth())
            }

            !access.background -> {
                Text("One more step", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    text = "To change the music while the app is closed, open settings, choose Location, " +
                        "and pick \"Allow all the time\". Until then zones only work while the app is open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                PersonaButton("Open settings", onOpenSettings, modifier = Modifier.fillMaxWidth())
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
            text = if (zoneCount == 0) "Add your first zone below." else "Watching ${status.count} active zone${if (status.count == 1) "" else "s"}.",
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
    musicLabel: String,
    onRadiusChange: (Float) -> Unit,
    onChangeMusic: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val zone = row.zone

    PersonaCard(modifier = Modifier.fillMaxWidth()) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            PersonaButton("Change music", onChangeMusic, modifier = Modifier.weight(1f))
            PersonaButton("Delete", onDelete, modifier = Modifier.weight(1f))
        }
    }
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
