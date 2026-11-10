package com.example.personabgm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.model.PlaybackScene

/** Shared Normal/Battle selector for Home, Now Playing and My Music. */
@Composable
fun PlaybackSceneSwitch(
    scene: PlaybackScene,
    onSceneChange: (PlaybackScene) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SceneOption(
                label = "♫  Normal Mode",
                selected = scene == PlaybackScene.NORMAL,
                selectedColor = Color(0xFFD4F500),
                onClick = { onSceneChange(PlaybackScene.NORMAL) },
                modifier = Modifier.weight(1f),
            )
            SceneOption(
                label = "▰  Battle Mode",
                selected = scene == PlaybackScene.BATTLE,
                selectedColor = Color(0xFF8AB8FF),
                onClick = { onSceneChange(PlaybackScene.BATTLE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SceneOption(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = if (selected) Color(0xFF111318) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .background(
                color = if (selected) selectedColor else Color.Transparent,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            maxLines = 1,
        )
    }
}
