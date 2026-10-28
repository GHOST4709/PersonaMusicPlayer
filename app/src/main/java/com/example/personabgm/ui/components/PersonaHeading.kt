package com.example.personabgm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.ui.theme.LocalPersonaStyle

/**
 * Screen heading. This is where each theme spends its personality:
 *  - Persona 3: plain light type with a short cyan rule.
 *  - Persona 4: a tilted yellow pop badge.
 *  - Persona 5: tilted slanted slab with an offset white shadow slab.
 */
@Composable
fun PersonaHeading(text: String, modifier: Modifier = Modifier) {
    val style = LocalPersonaStyle.current
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.headlineMedium

    when (style.theme) {
        GameTheme.PERSONA_3_RELOAD -> Column(modifier) {
            Text(text = text, style = textStyle, color = colors.onBackground)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(56.dp)
                    .height(3.dp)
                    .background(colors.secondary, CircleShape),
            )
        }

        GameTheme.PERSONA_4_REVIVAL -> Box(
            modifier
                .rotate(-2f)
                .background(colors.primary, style.shapes.badge)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = text, style = textStyle, color = colors.onPrimary)
        }

        GameTheme.PERSONA_5_ROYAL -> Box(modifier.rotate(-3f)) {
            Box(
                Modifier
                    .offset(x = 6.dp, y = 6.dp)
                    .matchParentSize()
                    .background(colors.secondary, style.shapes.badge),
            )
            Box(
                Modifier
                    .background(colors.primary, style.shapes.badge)
                    .padding(horizontal = 28.dp, vertical = 8.dp),
            ) {
                Text(text = text, style = textStyle, color = colors.onPrimary)
            }
        }
    }
}
