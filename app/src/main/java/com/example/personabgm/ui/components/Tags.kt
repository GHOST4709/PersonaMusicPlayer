package com.example.personabgm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Small outlined label (conditions, "Saved", context chips). */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    Box(modifier.border(1.dp, colors.outline, shape).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

/** Small filled label in the accent color (state: "Playing", "Inside", "Roaming default"). */
@Composable
fun StatusBadge(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .background(colors.secondary, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.onSecondary)
    }
}
