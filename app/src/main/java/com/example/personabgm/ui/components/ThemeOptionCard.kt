package com.example.personabgm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.example.personabgm.ui.theme.LocalPersonaStyle
import com.example.personabgm.ui.theme.PersonaPalette
import com.example.personabgm.ui.theme.PersonaStyle

/** A selectable theme with its colour swatches; shared by Home and onboarding. */
@Composable
fun ThemeOptionCard(
    style: PersonaStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PersonaCard(
        modifier = modifier.fillMaxWidth(),
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

/** Decorative colour chips (primary, accent, background); hidden from screen readers. */
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
