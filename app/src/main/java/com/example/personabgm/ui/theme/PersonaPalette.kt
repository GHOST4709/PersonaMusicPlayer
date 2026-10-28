package com.example.personabgm.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.personabgm.domain.model.GameTheme

/**
 * Color tokens for one Persona theme.
 *
 * Primary / accent / background are the spec 3.1 values. The remaining tokens (on-colors, surface
 * tints, outline, error) are derived and are checked by PaletteContrastTest.
 *
 * Usage rule: Persona 3's primary (cobalt) is only ~3.4:1 on its background, so it is used for
 * fills, large headings, and icons. Small text on that theme uses the accent (cyan).
 */
@Immutable
data class PersonaPalette(
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color,
    val onError: Color,
)

object PersonaPalettes {
    val Persona3Reload = PersonaPalette(
        primary = Color(0xFF0055FF),          // Cobalt Blue
        onPrimary = Color(0xFFFFFFFF),
        accent = Color(0xFF00E5FF),           // Electric Cyan
        onAccent = Color(0xFF0A0E17),
        background = Color(0xFF0A0E17),       // Dark Slate
        onBackground = Color(0xFFE8EEF9),
        surface = Color(0xFF111827),
        onSurface = Color(0xFFE8EEF9),
        surfaceVariant = Color(0xFF1A2438),
        onSurfaceVariant = Color(0xFF9DB0D0),
        outline = Color(0xFF2A3550),
        error = Color(0xFFFF5C7A),
        onError = Color(0xFF10040A),
    )

    val Persona4Revival = PersonaPalette(
        primary = Color(0xFFFFD000),          // TV Yellow
        onPrimary = Color(0xFF181818),
        accent = Color(0xFFFF6600),           // Pop Orange
        onAccent = Color(0xFF181818),
        background = Color(0xFF181818),       // Off-Black
        onBackground = Color(0xFFFFF4D6),
        surface = Color(0xFF232323),
        onSurface = Color(0xFFFFF4D6),
        surfaceVariant = Color(0xFF2E2E2E),
        onSurfaceVariant = Color(0xFFC9BE9A),
        outline = Color(0xFF4A4A4A),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF1A0505),
    )

    val Persona5Royal = PersonaPalette(
        primary = Color(0xFFD80027),          // Crimson Red
        onPrimary = Color(0xFFFFFFFF),
        accent = Color(0xFFFFFFFF),           // Pure White
        onAccent = Color(0xFF101010),
        background = Color(0xFF101010),       // Obsidian Black
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF1C1C1C),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF262626),
        onSurfaceVariant = Color(0xFFBDBDBD),
        outline = Color(0xFF4A4A4A),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF1A0505),
    )

    fun of(theme: GameTheme): PersonaPalette = when (theme) {
        GameTheme.PERSONA_3_RELOAD -> Persona3Reload
        GameTheme.PERSONA_4_REVIVAL -> Persona4Revival
        GameTheme.PERSONA_5_ROYAL -> Persona5Royal
    }
}
