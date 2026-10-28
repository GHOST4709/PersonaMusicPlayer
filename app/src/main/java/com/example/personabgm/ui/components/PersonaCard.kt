package com.example.personabgm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.personabgm.ui.theme.LocalPersonaStyle

/**
 * Themed surface. Pass [selected] (non-null) plus [onClick] to make it a radio-style option;
 * the selected state is exposed to accessibility services and the selected border uses the
 * accent color, so callers should also add a non-color cue (text or badge).
 */
@Composable
fun PersonaCard(
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalPersonaStyle.current
    val colors = MaterialTheme.colorScheme
    val shape = style.shapes.card
    val border = if (selected == true) {
        BorderStroke(2.dp, colors.secondary)
    } else {
        BorderStroke(1.dp, colors.outline)
    }

    val base = modifier
        .clip(shape)
        .background(colors.surface)
        .border(border, shape)

    val interactive = when {
        selected != null && onClick != null ->
            base.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        onClick != null -> base.clickable(onClick = onClick)
        else -> base
    }

    Column(modifier = interactive.padding(20.dp), content = content)
}
