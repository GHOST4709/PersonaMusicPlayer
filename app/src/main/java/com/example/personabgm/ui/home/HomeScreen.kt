package com.example.personabgm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.ui.components.PersonaButton
import com.example.personabgm.ui.components.PersonaCard
import com.example.personabgm.ui.components.PersonaHeading
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.theme.LocalPersonaStyle
import com.example.personabgm.ui.theme.PersonaPalette
import com.example.personabgm.ui.theme.PersonaStyle
import com.example.personabgm.ui.theme.PersonaStyles

@Composable
fun HomeScreen(
    selectedTheme: GameTheme,
    onThemeSelected: (GameTheme) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PersonaHeading(text = "Now playing") }

        item { NowPlayingCard() }

        item {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

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

        item {
            PersonaButton(
                text = "Settings",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Placeholder until the audio engine lands (Phase 3). */
@Composable
private fun NowPlayingCard() {
    val colors = MaterialTheme.colorScheme
    PersonaCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Nothing playing yet",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Text(
            text = "Playback and zones arrive in the next builds. Pick a theme below to preview the look.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ThemeOptionCard(
    style: PersonaStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    PersonaCard(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Swatches(palette = style.palette)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = style.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (selected) ActiveBadge()
        }
    }
}

/** Decorative color chips (primary, accent, background); hidden from screen readers. */
@Composable
private fun Swatches(palette: PersonaPalette) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        listOf(palette.primary, palette.accent, palette.background).forEach { color ->
            Swatch(color = color, outline = palette.outline)
        }
    }
}

@Composable
private fun Swatch(color: Color, outline: Color) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, outline, CircleShape),
    )
}

@Composable
private fun ActiveBadge() {
    val colors = MaterialTheme.colorScheme
    val shape = LocalPersonaStyle.current.shapes.badge
    Box(
        Modifier
            .background(colors.secondary, shape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Active",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSecondary,
        )
    }
}
