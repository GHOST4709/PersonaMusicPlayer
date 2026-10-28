package com.example.personabgm.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.personabgm.domain.model.GameTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 acceptance: every theme passes WCAG contrast for the way its colors are used.
 * Thresholds: 4.5 for normal text, 7 for primary body text, 3.0 for large text and graphics.
 */
class PaletteContrastTest {

    private fun check(theme: GameTheme, label: String, fg: Color, bg: Color, min: Float) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$theme $label: contrast $ratio is below $min", ratio >= min)
    }

    @Test
    fun allThemesMeetContrastTargets() {
        GameTheme.entries.forEach { theme ->
            val p = PersonaPalettes.of(theme)
            check(theme, "onPrimary on primary (button text)", p.onPrimary, p.primary, 4.5f)
            check(theme, "onAccent on accent", p.onAccent, p.accent, 4.5f)
            check(theme, "onBackground on background", p.onBackground, p.background, 7f)
            check(theme, "onSurface on surface", p.onSurface, p.surface, 7f)
            check(theme, "onSurfaceVariant on surface", p.onSurfaceVariant, p.surface, 4.5f)
            check(theme, "onSurfaceVariant on surfaceVariant", p.onSurfaceVariant, p.surfaceVariant, 4.5f)
            check(theme, "accent on background (small accent text)", p.accent, p.background, 4.5f)
            check(theme, "primary on background (large text, graphics)", p.primary, p.background, 3.0f)
            check(theme, "onError on error", p.onError, p.error, 4.5f)
        }
    }

    @Test
    fun brandColorsMatchSpec() {
        // Spec 3.1: primary, accent, and background per theme.
        val p3 = PersonaPalettes.Persona3Reload
        assertTrue(p3.primary == Color(0xFF0055FF) && p3.accent == Color(0xFF00E5FF) && p3.background == Color(0xFF0A0E17))
        val p4 = PersonaPalettes.Persona4Revival
        assertTrue(p4.primary == Color(0xFFFFD000) && p4.accent == Color(0xFFFF6600) && p4.background == Color(0xFF181818))
        val p5 = PersonaPalettes.Persona5Royal
        assertTrue(p5.primary == Color(0xFFD80027) && p5.accent == Color(0xFFFFFFFF) && p5.background == Color(0xFF101010))
    }
}
