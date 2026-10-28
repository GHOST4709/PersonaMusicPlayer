package com.example.personabgm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personabgm.ui.theme.LocalPersonaStyle
import com.example.personabgm.ui.theme.LocalReduceMotion

/**
 * Full-screen loading state for the manifest fetch (spec 3.2), using the active theme's copy
 * ("Connecting to Metaverse..." or "Tuning TV..."). The progress bar is hidden under reduce-motion.
 */
@Composable
fun ConnectingScreen(
    modifier: Modifier = Modifier,
    message: String = LocalPersonaStyle.current.loadingMessage,
) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        if (!reduceMotion) {
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(160.dp),
                color = colors.secondary,
                trackColor = colors.outline,
            )
        }
    }
}

/**
 * Skeleton placeholder shimmering in the theme's primary color; used until the map renders
 * (spec 3.2). Holds a static mid-tone highlight under reduce-motion.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val base = colors.primary.copy(alpha = 0.16f)
    val highlight = colors.primary.copy(alpha = 0.38f)

    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val p = if (reduceMotion) 0.5f else progress
                val w = size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(w * (p * 2f - 1f), 0f),
                        end = Offset(w * (p * 2f), size.height),
                    ),
                )
            },
    )
}
