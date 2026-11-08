package com.example.personabgm.ui.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.permissions.ApiLevel
import com.example.personabgm.domain.permissions.OemTip
import com.example.personabgm.domain.permissions.OnboardingPlan
import com.example.personabgm.domain.permissions.OnboardingStep
import com.example.personabgm.domain.permissions.PermissionSnapshot
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.components.StatusBadge
import com.example.personabgm.ui.components.TagChip
import com.example.personabgm.ui.components.ThemeOptionCard
import com.example.personabgm.ui.components.headingSemantics
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.permissions.PermissionActions
import com.example.personabgm.ui.theme.PersonaStyles

/**
 * Step-by-step setup (spec 3.2). Every step explains why it is asked, shows whether it is already done,
 * and can be skipped: the Permission Health card on Home remembers what is still missing.
 */
@Composable
fun OnboardingScreen(
    snapshot: PermissionSnapshot,
    selectedTheme: GameTheme,
    oemTip: OemTip?,
    actions: PermissionActions,
    onThemeSelected: (GameTheme) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val steps = remember { OnboardingPlan.steps(Build.VERSION.SDK_INT) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(steps.indices)]
    val isLast = index >= steps.lastIndex
    val satisfied = OnboardingPlan.isSatisfied(step, snapshot)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PersonaHeading(text = "Set up") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Step ${index + 1} of ${steps.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground,
                )
                LinearProgressIndicator(
                    progress = { (index + 1f) / steps.size },
                    // The "Step 2 of 6" text above already says this; do not read it twice.
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                    color = colors.secondary,
                    trackColor = colors.outline,
                )
            }
        }

        item {
            PersonaCard(modifier = Modifier.fillMaxWidth()) {
                StepContent(step, snapshot, satisfied, oemTip, actions)
            }
        }

        if (step == OnboardingStep.WELCOME) {
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
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (index > 0) {
                    PersonaButton(text = "Back", onClick = { index-- }, modifier = Modifier.weight(1f))
                }
                PersonaButton(
                    text = when {
                        isLast && satisfied -> "Finish"
                        isLast -> "Skip and finish"
                        satisfied -> "Next"
                        else -> "Skip for now"
                    },
                    onClick = { if (isLast) onFinish() else index++ },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StepContent(
    step: OnboardingStep,
    snapshot: PermissionSnapshot,
    satisfied: Boolean,
    oemTip: OemTip?,
    actions: PermissionActions,
) {
    when (step) {
        OnboardingStep.WELCOME -> StepFrame(
            title = "Music that follows your day",
            body = "Persona BGM changes your background music as you move between places, as the weather turns, " +
                "and as day becomes night. A few quick steps will get it working even when your screen is off. " +
                "First, pick a look; you can change it any time.",
            status = null,
        )

        OnboardingStep.LOCATION -> StepFrame(
            title = "Your location",
            body = "Zones need to know when you arrive at a place. Your position stays on this phone; only a rounded " +
                "position (about 1 km) is used to look up the weather.",
            status = Status(satisfied, if (satisfied) "Allowed" else "Not yet"),
        ) {
            if (snapshot.approximateOnly) {
                Warning(
                    "You allowed approximate location only. Zones need precise location. Tap the button and " +
                        "choose Precise.",
                )
            }
            if (!satisfied) {
                PersonaButton("Allow location", actions::requestLocation, emphasized = true, modifier = Modifier.fillMaxWidth())
                LinkButton("Not seeing a prompt? Open app settings", actions::openAppSettings)
            }
        }

        OnboardingStep.BACKGROUND_LOCATION -> StepFrame(
            title = "Location all the time",
            body = "To change your music while the app is closed, Android needs you to choose \"Allow all the " +
                "time\". Android will open the app's location settings; pick that option, then come back here.",
            status = Status(satisfied, if (satisfied) "Allowed" else "Not yet"),
        ) {
            if (!OnboardingPlan.isReady(step, snapshot)) {
                Warning("Allow your location in the step before this one first.")
            } else if (!satisfied) {
                PersonaButton(
                    "Choose \"Allow all the time\"",
                    actions::requestBackgroundLocation,
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LinkButton("Open app settings", actions::openAppSettings)
            }
            if (Build.VERSION.SDK_INT >= ApiLevel.R) {
                Text(
                    text = "Also turn off \"Pause app activity if unused\" so Android doesn't remove this permission later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinkButton("Open that setting", actions::openAutoRevokeSettings)
            }
        }

        OnboardingStep.NOTIFICATIONS -> StepFrame(
            title = "Playback controls",
            body = "Notifications show what's playing and give you Pause and Next on your lock screen and in the " +
                "notification shade. Music keeps playing without them.",
            status = Status(satisfied, if (satisfied) "Allowed" else "Not yet"),
        ) {
            if (!satisfied) {
                PersonaButton("Show controls", actions::requestNotifications, emphasized = true, modifier = Modifier.fillMaxWidth())
            }
        }

        OnboardingStep.BATTERY -> StepFrame(
            title = "Keep zones working",
            body = "Some phones stop background apps to save battery, which would stop your zones from noticing " +
                "that you arrived. Allowing background activity prevents that.",
            status = Status(satisfied, if (satisfied) "Allowed" else "Not yet"),
        ) {
            if (!satisfied) {
                PersonaButton("Allow background activity", actions::requestBatteryExemption, emphasized = true, modifier = Modifier.fillMaxWidth())
                LinkButton("Open battery settings", actions::openBatterySettings)
            }
            if (oemTip != null) OemTipBlock(oemTip, actions)
        }

        OnboardingStep.BLUETOOTH -> StepFrame(
            title = "Headphones and car",
            body = "Lets the app notice your Bluetooth headphones or car. Music pauses when they disconnect either " +
                "way, so you can skip this.",
            status = Status(satisfied, if (satisfied) "Allowed" else "Optional"),
        ) {
            if (!satisfied) {
                PersonaButton("Allow Bluetooth", actions::requestBluetooth, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private data class Status(val done: Boolean, val label: String)

@Composable
private fun StepFrame(
    title: String,
    body: String,
    status: Status?,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f).headingSemantics(),
        )
        if (status != null) {
            if (status.done) StatusBadge(status.label) else TagChip(status.label)
        }
    }
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun Warning(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
    ) { Text(text) }
}

@Composable
internal fun OemTipBlock(tip: OemTip, actions: PermissionActions) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = "${tip.brand} phones also need this",
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(text = tip.advice, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    LinkButton("More help for ${tip.brand}") { actions.openUrl(tip.moreInfoUrl) }
}
