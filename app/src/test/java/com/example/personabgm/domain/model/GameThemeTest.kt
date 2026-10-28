package com.example.personabgm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GameThemeTest {
    @Test
    fun knownNamesParse() {
        assertEquals(GameTheme.PERSONA_4_REVIVAL, GameTheme.fromNameOrDefault("PERSONA_4_REVIVAL"))
    }

    @Test
    fun unknownOrMissingFallsBackToDefault() {
        assertEquals(GameTheme.Default, GameTheme.fromNameOrDefault("PERSONA_9_FUTURE"))
        assertEquals(GameTheme.Default, GameTheme.fromNameOrDefault(null))
    }
}
