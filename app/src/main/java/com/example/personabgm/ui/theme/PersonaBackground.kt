package com.example.personabgm.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.model.GameTheme
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Full-screen themed backdrop. The base color follows the animated color scheme; the pattern
 * layer cross-fades between themes. Patterns are decoration only and sit below all content.
 */
@Composable
fun PersonaBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalPersonaStyle.current
    val reduceMotion = LocalReduceMotion.current
    val fadeSpec: FiniteAnimationSpec<Float> =
        if (reduceMotion) snap<Float>() else tween<Float>(durationMillis = 450)

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Crossfade(targetState = style.theme, animationSpec = fadeSpec, label = "backgroundPattern") { theme ->
            val palette = PersonaPalettes.of(theme)
            when (theme) {
                GameTheme.PERSONA_3_RELOAD -> AquaPattern(palette)
                GameTheme.PERSONA_4_REVIVAL -> CrtPattern(palette)
                GameTheme.PERSONA_5_ROYAL -> ComicPattern(palette)
            }
        }
        content()
    }
}

/** Persona 3: dark aquatic gradient with three slow, low-contrast waves. */
@Composable
private fun AquaPattern(palette: PersonaPalette) {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "aqua")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 14_000, easing = LinearEasing)),
        label = "aquaPhase",
    )

    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, palette.primary.copy(alpha = 0.22f)),
                startY = size.height * 0.35f,
                endY = size.height,
            ),
        )

        val currentPhase = if (reduceMotion) 0f else phase
        val amplitude = 14.dp.toPx()
        val step = 10.dp.toPx()
        repeat(3) { i ->
            val baseY = size.height * (0.78f + 0.06f * i)
            val wave = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width + step) {
                    val angle = (x / size.width) * 2f * PI.toFloat() * (1.2f + 0.4f * i) +
                        currentPhase * (1f + 0.25f * i)
                    lineTo(x, baseY + amplitude * sin(angle))
                    x += step
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(wave, color = palette.accent.copy(alpha = 0.05f + 0.03f * i))
        }
    }
}

/** Persona 4: halftone dots fading out from the top-right corner, plus CRT scanlines. */
@Composable
private fun CrtPattern(palette: PersonaPalette) {
    Canvas(Modifier.fillMaxSize()) {
        val spacing = 14.dp.toPx()
        val maxRadius = 5.dp.toPx()
        val reach = size.width * 0.9f
        var y = 0f
        var row = 0
        while (y <= size.height * 0.5f) {
            var x = if (row % 2 == 0) 0f else spacing / 2f
            while (x <= size.width) {
                val distance = hypot(size.width - x, y)
                val strength = (1f - distance / reach).coerceIn(0f, 1f)
                val radius = maxRadius * strength
                if (radius > 0.6f) {
                    drawCircle(
                        color = palette.primary.copy(alpha = 0.28f),
                        radius = radius,
                        center = Offset(x, y),
                    )
                }
                x += spacing
            }
            y += spacing * 0.866f
            row++
        }

        val lineHeight = 2.dp.toPx()
        val gap = 4.dp.toPx()
        var lineY = 0f
        while (lineY < size.height) {
            drawRect(
                color = Color.Black.copy(alpha = 0.16f),
                topLeft = Offset(0f, lineY),
                size = Size(size.width, lineHeight),
            )
            lineY += lineHeight + gap
        }
    }
}

/** Persona 5: a diagonal crimson band, a corner wedge, and a few hairline slashes. */
@Composable
private fun ComicPattern(palette: PersonaPalette) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val band = Path().apply {
            moveTo(0f, h * 0.74f)
            lineTo(w, h * 0.48f)
            lineTo(w, h * 0.60f)
            lineTo(0f, h * 0.86f)
            close()
        }
        drawPath(band, color = palette.primary.copy(alpha = 0.30f))
        drawLine(
            color = Color.White.copy(alpha = 0.10f),
            start = Offset(0f, h * 0.74f),
            end = Offset(w, h * 0.48f),
            strokeWidth = 3.dp.toPx(),
        )

        val wedge = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.42f, 0f)
            lineTo(0f, h * 0.14f)
            close()
        }
        drawPath(wedge, color = palette.primary.copy(alpha = 0.18f))

        repeat(4) { i ->
            val x = w * (0.55f + 0.10f * i)
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(x, 0f),
                end = Offset(x - h * 0.25f, h * 0.5f),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}
