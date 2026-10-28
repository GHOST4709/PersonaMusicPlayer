package com.example.personabgm.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.personabgm.ui.haptics.LocalHaptics
import com.example.personabgm.ui.theme.LocalPersonaStyle

@Composable
fun PersonaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val style = LocalPersonaStyle.current
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current

    Button(
        onClick = {
            if (emphasized) haptics.emphasize() else haptics.tick()
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = style.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
