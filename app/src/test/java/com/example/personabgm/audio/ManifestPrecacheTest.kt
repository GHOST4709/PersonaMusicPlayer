package com.example.personabgm.audio

import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestPrecacheTest {
    private fun t(id: String, theme: GameTheme, group: String? = null) = Track(
        id, theme, id, "A", TrackTime.ALL, TrackWeather.ALL, group, "https://x.example/$id.m4a", null,
    )

    @Test fun includesEachThemeDefaultAndItsVariantsWithoutDuplicates() {
        val m = Manifest(
            version = 2,
            themeDefaults = mapOf(
                GameTheme.PERSONA_3_RELOAD to "p3",
                GameTheme.PERSONA_4_REVIVAL to "p4a",
                GameTheme.PERSONA_5_ROYAL to "missing",
            ),
            tracks = listOf(
                t("p3", GameTheme.PERSONA_3_RELOAD),
                t("p4a", GameTheme.PERSONA_4_REVIVAL, "g4"),
                t("p4b", GameTheme.PERSONA_4_REVIVAL, "g4"),
                t("extra", GameTheme.PERSONA_3_RELOAD),
            ),
        )
        assertEquals(listOf("p3", "p4a", "p4b"), m.precacheTracks().map { it.id })
    }

    @Test fun emptyDefaultsMeanNothingToPrecache() {
        assertEquals(emptyList<Track>(), Manifest(1, emptyMap(), listOf(t("a", GameTheme.PERSONA_3_RELOAD))).precacheTracks())
    }

    @Test fun zoneAssignedTracksAndTheirVariantsAreIncludedWithoutDuplicates() {
        val m = Manifest(
            version = 2,
            themeDefaults = mapOf(GameTheme.PERSONA_3_RELOAD to "p3"),
            tracks = listOf(
                t("p3", GameTheme.PERSONA_3_RELOAD),
                t("zone_a", GameTheme.PERSONA_4_REVIVAL, "gz"),
                t("zone_b", GameTheme.PERSONA_4_REVIVAL, "gz"),
                t("unused", GameTheme.PERSONA_5_ROYAL),
            ),
        )
        assertEquals(
            listOf("p3", "zone_a", "zone_b"),
            m.precacheTracks(listOf("zone_a", "p3", "ghost")).map { it.id },
        )
    }
}
