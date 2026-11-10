package com.example.personabgm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import android.os.Build
import com.example.personabgm.BuildConfig
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.personabgm.audio.CacheSnapshot
import com.example.personabgm.domain.permissions.ApiLevel
import com.example.personabgm.domain.permissions.OemTip
import com.example.personabgm.domain.permissions.PermissionSnapshot
import com.example.personabgm.ui.onboarding.OemTipBlock
import com.example.personabgm.ui.permissions.PermissionActions
import com.example.personabgm.audio.MediaCache
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.domain.model.CrossfadeCurve
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.components.PersonaSliderRow
import com.example.personabgm.ui.components.PersonaSwitchRow
import com.example.personabgm.ui.components.headingSemantics
import com.example.personabgm.ui.formatBytes
import com.example.personabgm.ui.haptics.LocalHaptics
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    cache: CacheSnapshot,
    snapshot: PermissionSnapshot,
    oemTip: OemTip?,
    actions: PermissionActions,
    onRunSetup: () -> Unit,
    onOpened: () -> Unit,
    onOpenScenarios: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCrossfadeDurationChange: (Int) -> Unit,
    onLinearCurveChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAudioCacheLimitChange: (Int) -> Unit,
    musicAccessConfigured: Boolean,
    musicAccessError: String?,
    onSaveMusicAccessToken: (String) -> Unit,
    onClearMusicAccessToken: () -> Unit,
    onFastZoneDetectionChange: (Boolean) -> Unit,
    onOpenDebug: (() -> Unit)?,
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
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                Text("Scenarios", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    "Create separate All, Persona 3, Persona 4 and Persona 5 rules for time, weather, location and playback scene.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                PersonaButton("Manage scenario rules", onOpenScenarios, modifier = Modifier.fillMaxWidth())
            }
        }

        item { SectionTitle("Playback & Audio") }
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                PersonaSliderRow(
                    title = "Volume",
                    value = settings.globalVolume,
                    range = 0f..1f,
                    steps = 0,
                    format = { "${(it * 100).roundToInt()}%" },
                    onCommit = onVolumeChange,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                PersonaSliderRow(
                    title = "Crossfade length",
                    value = settings.crossfadeDurationMs / 1000f,
                    range = 0.5f..8f,
                    steps = 14, // half-second steps
                    format = { String.format(Locale.US, "%.1f s", it) },
                    onCommit = { onCrossfadeDurationChange((it * 1000).roundToInt()) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                PersonaSwitchRow(
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
                PersonaSwitchRow(
                    title = "Download on Wi-Fi only",
                    description = "Pre-downloads your themes' tracks so they start instantly, even offline.",
                    checked = settings.precacheWifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                PersonaSliderRow(
                    title = "Cache budget",
                    value = settings.audioCacheLimitMb.toFloat(),
                    range = 256f..2048f,
                    steps = 6,
                    format = { if (it >= 1024f) String.format(Locale.US, "%.1f GB", it / 1024f) else "${it.roundToInt()} MB" },
                    onCommit = { onAudioCacheLimitChange(it.roundToInt()) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                Text(
                    text = "Saved audio: ${formatBytes(cache.totalBytes)} of ${formatBytes(settings.audioCacheLimitMb.toLong() * MediaCache.BYTES_PER_MB)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Clearing saved audio keeps the currently loaded track and any incoming crossfade track until playback releases them.",
                    style = MaterialTheme.typography.bodySmall,
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

        item { SectionTitle("Private music library") }
        item {
            var tokenInput by remember { mutableStateOf("") }
            var tokenValidationError by remember { mutableStateOf<String?>(null) }
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (musicAccessConfigured) "Private access token is stored on this device" else "Private access is not configured",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Paste the 64-character token generated for your Cloudflare Worker. Android Keystore encrypts it at rest; it is not bundled into the APK.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it.trim().take(64); tokenValidationError = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("64-character access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                val validToken = tokenInput.trim().matches(Regex("(?i)^[a-f0-9]{64}$"))
                if (tokenValidationError != null || musicAccessError != null) {
                    Text(
                        text = tokenValidationError ?: musicAccessError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PersonaButton(
                    text = "Save private access token",
                    onClick = {
                        if (validToken) { onSaveMusicAccessToken(tokenInput.trim()); tokenInput = "" }
                        else tokenValidationError = "Enter all 64 hexadecimal characters from your Worker secret."
                    },
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (musicAccessConfigured) {
                    PersonaButton(text = "Remove stored token", onClick = onClearMusicAccessToken, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = "The R2 bucket remains private. Losing this token does not delete audio already cached on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        item { SectionTitle("Reliability") }
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Background activity: ${if (snapshot.batteryExempt) "Allowed" else "Restricted"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Zones need the app to keep running with the screen off. Battery savers on some phones stop that.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                if (!snapshot.batteryExempt) {
                    PersonaButton("Allow background activity", actions::requestBatteryExemption, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                PersonaButton("Open battery settings", actions::openBatterySettings, modifier = Modifier.fillMaxWidth())

                if (Build.VERSION.SDK_INT >= ApiLevel.R) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colors.outline)
                    Text(
                        text = "Android removes permissions from apps you don't open for a while. Turn that off for this app so zones keep working.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    PersonaButton("Keep permissions", actions::openAutoRevokeSettings, modifier = Modifier.fillMaxWidth())
                }

                if (oemTip != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colors.outline)
                    OemTipBlock(oemTip, actions)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colors.outline)
                PersonaButton("Run setup again", onRunSetup, modifier = Modifier.fillMaxWidth())
            }
        }

        item { SectionTitle("Zones") }
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                PersonaSwitchRow(
                    title = "Faster zone detection",
                    description = "While the app is open, uses your location to notice arriving and leaving within " +
                        "seconds. Zones still work in the background either way.",
                    checked = settings.fastZoneDetection,
                    onCheckedChange = onFastZoneDetectionChange,
                )
            }
        }

        item { SectionTitle("Appearance") }
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                PersonaSwitchRow(
                    title = "Haptic feedback",
                    description = "Short vibrations when you navigate, switch themes, or drop a pin.",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = onHapticsChange,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.outline)
                PersonaSwitchRow(
                    title = "Reduce motion",
                    description = "Stops background animation and screen transitions. The system " +
                        "\"Remove animations\" setting is also respected.",
                    checked = settings.reduceMotion,
                    onCheckedChange = onReduceMotionChange,
                )
            }
        }

        item { SectionTitle("About") }
        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Persona BGM ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Headings use the Rajdhani, Lilita One, and Anton typefaces under the SIL Open Font " +
                        "License 1.1. The license texts are in the app's assets/licenses folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (onOpenDebug != null) {
                    Spacer(Modifier.height(12.dp))
                    PersonaButton("Debug panel", onOpenDebug, modifier = Modifier.fillMaxWidth())
                }
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
        modifier = Modifier.headingSemantics().padding(top = 8.dp),
    )
}
