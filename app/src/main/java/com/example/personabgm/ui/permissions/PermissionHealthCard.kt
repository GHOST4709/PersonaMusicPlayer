package com.example.personabgm.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.permissions.HealthIssue
import com.example.personabgm.domain.permissions.PermissionHealth
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard

private data class IssueCopy(val title: String, val body: String, val fixLabel: String)

private fun copyFor(issue: HealthIssue) = when (issue) {
    HealthIssue.LOCATION_MISSING -> IssueCopy(
        "Allow location",
        "Zones can't tell when you arrive or leave without it.",
        "Allow",
    )
    HealthIssue.LOCATION_APPROXIMATE_ONLY -> IssueCopy(
        "Precise location is off",
        "Zones need your exact location. Choose Precise when asked, or switch it on in the app's settings.",
        "Allow precise",
    )
    HealthIssue.BACKGROUND_LOCATION_MISSING -> IssueCopy(
        "Allow location all the time",
        "Without it, zones only change your music while the app is open.",
        "Open location settings",
    )
    HealthIssue.NOTIFICATIONS_OFF -> IssueCopy(
        "Notifications are off",
        "Playback controls won't show on your lock screen or in the notification shade.",
        "Turn on",
    )
    HealthIssue.BATTERY_RESTRICTED -> IssueCopy(
        "Battery saver may stop your zones",
        "Let Persona BGM run in the background so zones keep working with the screen off.",
        "Allow",
    )
}

/**
 * Shows the most important thing that is wrong with permissions, with a one-tap fix (spec 3.2). Critical
 * location problems cannot be hidden; the rest can be dismissed until the next launch.
 */
@Composable
fun PermissionHealthCard(
    issues: List<HealthIssue>,
    onFix: (HealthIssue) -> Unit,
    onDismiss: (HealthIssue) -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val issue = issues.firstOrNull() ?: return
    val colors = MaterialTheme.colorScheme
    val copy = copyFor(issue)
    val critical = PermissionHealth.isCritical(issue)

    PersonaCard(modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(text = copy.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(
            text = copy.body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonaButton(text = copy.fixLabel, onClick = { onFix(issue) }, emphasized = true, modifier = Modifier.weight(1f))
            if (critical) {
                PersonaButton(text = "App settings", onClick = onOpenAppSettings, modifier = Modifier.weight(1f))
            } else {
                PersonaButton(text = "Not now", onClick = { onDismiss(issue) }, modifier = Modifier.weight(1f))
            }
        }
        if (issues.size > 1) {
            Text(
                text = "${issues.size - 1} more to check after this one.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** High-contrast banner shown when the device's Location Services are off (spec 3.2). */
@Composable
fun GpsBanner(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.error)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Location Services are off, so zones can't change your music.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onError,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onTurnOn, colors = ButtonDefaults.textButtonColors(contentColor = colors.onError)) {
            Text(text = "Turn on", style = MaterialTheme.typography.labelLarge)
        }
    }
}
