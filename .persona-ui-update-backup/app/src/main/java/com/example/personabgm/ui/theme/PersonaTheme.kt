package com.example.personabgm.ui.theme

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.personabgm.domain.model.GameTheme

/** Non-color theme data (shapes, copy, ids) for the active theme. */
val LocalPersonaStyle = staticCompositionLocalOf<PersonaStyle> {
    error("PersonaTheme is not provided. Wrap content in PersonaTheme { }.")
}

/** True when the in-app toggle or the system "Remove animations" setting asks for no motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Live-switching theme (spec 3.1). Colors cross-animate over 450 ms; typography and shapes
 * switch at once. With reduce-motion on, everything snaps.
 *
 * Read colors from MaterialTheme.colorScheme (animated), not from LocalPersonaStyle.palette
 * (which is always the target palette).
 */
@Composable
fun PersonaTheme(
    theme: GameTheme,
    reduceMotionSetting: Boolean,
    content: @Composable () -> Unit,
) {
    val style = remember(theme) { PersonaStyles.of(theme) }
    val reduceMotion = reduceMotionSetting || rememberSystemReduceMotion()
    val spec: AnimationSpec<Color> =
        if (reduceMotion) snap<Color>() else tween<Color>(durationMillis = 450)

    val palette = animatePalette(style.palette, spec)
    val colorScheme = remember(palette) { palette.toColorScheme() }
    val shapes = remember(style) {
        Shapes(
            extraSmall = style.shapes.badge,
            small = style.shapes.button,
            medium = style.shapes.card,
            large = style.shapes.card,
            extraLarge = style.shapes.card,
        )
    }

    CompositionLocalProvider(
        LocalPersonaStyle provides style,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = style.typography,
            shapes = shapes,
            content = content,
        )
    }
}

@Composable
private fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

@Composable
private fun animated(target: Color, spec: AnimationSpec<Color>): Color =
    animateColorAsState(targetValue = target, animationSpec = spec, label = "personaColor").value

@Composable
private fun animatePalette(target: PersonaPalette, spec: AnimationSpec<Color>): PersonaPalette =
    PersonaPalette(
        primary = animated(target.primary, spec),
        onPrimary = animated(target.onPrimary, spec),
        accent = animated(target.accent, spec),
        onAccent = animated(target.onAccent, spec),
        background = animated(target.background, spec),
        onBackground = animated(target.onBackground, spec),
        surface = animated(target.surface, spec),
        onSurface = animated(target.onSurface, spec),
        surfaceVariant = animated(target.surfaceVariant, spec),
        onSurfaceVariant = animated(target.onSurfaceVariant, spec),
        outline = animated(target.outline, spec),
        error = animated(target.error, spec),
        onError = animated(target.onError, spec),
    )

/**
 * Maps a palette to Material 3 roles. `secondary` and `tertiary` carry the accent, so small
 * accent-colored text should use `colorScheme.secondary`. Container roles are set explicitly,
 * otherwise Material falls back to its baseline purple tones.
 */
internal fun PersonaPalette.toColorScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primary,
    onPrimaryContainer = onPrimary,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = surfaceVariant,
    onSecondaryContainer = onSurface,
    tertiary = accent,
    onTertiary = onAccent,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = primary,
    outline = outline,
    outlineVariant = outline,
    error = error,
    onError = onError,
    surfaceDim = background,
    surfaceBright = surfaceVariant,
    surfaceContainerLowest = background,
    surfaceContainerLow = surface,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceVariant,
    surfaceContainerHighest = surfaceVariant,
)
