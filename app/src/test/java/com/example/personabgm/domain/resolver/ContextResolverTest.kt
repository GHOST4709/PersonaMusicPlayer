package com.example.personabgm.domain.resolver

import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.PlaybackContext
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import com.example.personabgm.domain.model.WeatherType
import com.example.personabgm.domain.model.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextResolverTest {

    private fun t(
        id: String,
        theme: GameTheme = GameTheme.PERSONA_4_REVIVAL,
        time: TrackTime = TrackTime.ALL,
        weather: TrackWeather = TrackWeather.ALL,
        group: String? = null,
    ) = Track(id, theme, id, "A", time, weather, group, "https://x.example/$id.m4a", null)

    private fun manifest(vararg tracks: Track, defaults: Map<GameTheme, String>) =
        Manifest(2, defaults, tracks.toList())

    private fun zone(assigned: String?) = Zone("z1", "Zone 1", 1.0, 1.0, 150f, assigned, true, 0L)

    private fun ctx(
        zone: Zone? = null,
        theme: GameTheme = GameTheme.PERSONA_4_REVIVAL,
        weather: WeatherType = WeatherType.CLEAR,
        time: TimeBucket = TimeBucket.DAY,
    ) = PlaybackContext(zone, theme, weather, time)

    private fun pick(m: Manifest, c: PlaybackContext) = ContextResolver.resolve(c, m)?.id

    // ---- stage 1: the base track ----

    @Test fun roamingUsesTheActiveThemesDefault() {
        val m = manifest(t("p4"), t("p5", GameTheme.PERSONA_5_ROYAL), defaults = mapOf(
            GameTheme.PERSONA_4_REVIVAL to "p4", GameTheme.PERSONA_5_ROYAL to "p5"))
        assertEquals("p4", pick(m, ctx()))
        assertEquals("p5", pick(m, ctx(theme = GameTheme.PERSONA_5_ROYAL)))
    }

    @Test fun zoneTrackBeatsThemeDefaultEvenWhenTimeOfDayAlwaysMatches() {
        // The v1 cascade bug: time of day always matched, so the zone track could never play.
        val m = manifest(t("default"), t("zone_track"), defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "default"))
        assertEquals("zone_track", pick(m, ctx(zone = zone("zone_track"), time = TimeBucket.DAY)))
        assertEquals("zone_track", pick(m, ctx(zone = zone("zone_track"), time = TimeBucket.NIGHT)))
    }

    @Test fun zoneWithoutAssignmentFallsBackToThemeDefault() {
        val m = manifest(t("default"), defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "default"))
        assertEquals("default", pick(m, ctx(zone = zone(null))))
    }

    @Test fun assignedTrackMissingFromManifestFallsBackToThemeDefault() {
        val m = manifest(t("default"), defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "default"))
        assertEquals("default", pick(m, ctx(zone = zone("ghost"))))
    }

    @Test fun noDefaultAndNoZoneYieldsNull() {
        val m = manifest(t("p4"), defaults = emptyMap())
        assertNull(pick(m, ctx()))
    }

    @Test fun zoneAssignedTrackWorksEvenForAThemeWithNoDefault() {
        val m = manifest(t("p4"), defaults = emptyMap())
        assertEquals("p4", pick(m, ctx(zone = zone("p4"))))
    }

    // ---- stage 2: variants ----

    private val p4Group = manifest(
        t("clear", weather = TrackWeather.CLEAR, group = "g"),
        t("rain", weather = TrackWeather.RAIN, group = "g"),
        defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "clear"),
    )

    @Test fun rainSelectsTheRainVariantAndClearSelectsTheClearOne() {
        assertEquals("rain", pick(p4Group, ctx(weather = WeatherType.RAIN)))
        assertEquals("clear", pick(p4Group, ctx(weather = WeatherType.CLEAR)))
    }

    @Test fun snowWithNoSnowVariantKeepsTheBaseTrack() {
        assertEquals("clear", pick(p4Group, ctx(weather = WeatherType.SNOW)))
    }

    @Test fun zoneAssignmentPicksTheSlotNotTheExactTrack() {
        // The zone is assigned the rain track, but it is clear: the clear variant of that slot plays.
        assertEquals("clear", pick(p4Group, ctx(zone = zone("rain"), weather = WeatherType.CLEAR)))
        assertEquals("rain", pick(p4Group, ctx(zone = zone("rain"), weather = WeatherType.RAIN)))
    }

    @Test fun weatherOutranksTimeOfDay() {
        val m = manifest(
            t("day_any", time = TrackTime.DAY, group = "g"),
            t("any_rain", weather = TrackWeather.RAIN, group = "g"),
            defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "day_any"),
        )
        assertEquals("any_rain", pick(m, ctx(weather = WeatherType.RAIN, time = TimeBucket.DAY)))
    }

    @Test fun matchingBothBeatsMatchingEither() {
        val m = manifest(
            t("day_any", time = TrackTime.DAY, group = "g"),
            t("any_rain", weather = TrackWeather.RAIN, group = "g"),
            t("day_rain", time = TrackTime.DAY, weather = TrackWeather.RAIN, group = "g"),
            defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "day_any"),
        )
        assertEquals("day_rain", pick(m, ctx(weather = WeatherType.RAIN, time = TimeBucket.DAY)))
        assertEquals("any_rain", pick(m, ctx(weather = WeatherType.RAIN, time = TimeBucket.NIGHT)))
    }

    @Test fun ineligibleVariantsAreNeverChosen() {
        val m = manifest(
            t("base", group = "g"),
            t("night_only", time = TrackTime.NIGHT, group = "g"),
            defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "base"),
        )
        assertEquals("base", pick(m, ctx(time = TimeBucket.DAY)))
        assertEquals("night_only", pick(m, ctx(time = TimeBucket.NIGHT)))
    }

    @Test fun nothingEligibleMeansTheBaseTrackPlaysRatherThanSilence() {
        // Spec sample: the P3 default is night+clear only. In the daytime it must still play.
        val m = manifest(
            t("night_clear", GameTheme.PERSONA_3_RELOAD, TrackTime.NIGHT, TrackWeather.CLEAR, "g"),
            defaults = mapOf(GameTheme.PERSONA_3_RELOAD to "night_clear"),
        )
        assertEquals("night_clear", pick(m, ctx(theme = GameTheme.PERSONA_3_RELOAD, time = TimeBucket.DAY)))
        assertEquals("night_clear", pick(m, ctx(theme = GameTheme.PERSONA_3_RELOAD, weather = WeatherType.RAIN)))
    }

    @Test fun equalScoresPreferTheBaseTrack() {
        val m = manifest(
            t("a_other", group = "g"),
            t("z_base", group = "g"),
            defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "z_base"),
        )
        assertEquals("z_base", pick(m, ctx()))
    }

    @Test fun equalScoresWithAnIneligibleBasePickTheLowestId() {
        val m = manifest(
            t("base", time = TrackTime.NIGHT, group = "g"),
            t("b", weather = TrackWeather.CLEAR, group = "g"),
            t("a", weather = TrackWeather.CLEAR, group = "g"),
            defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "base"),
        )
        assertEquals("a", pick(m, ctx(time = TimeBucket.DAY, weather = WeatherType.CLEAR)))
    }

    @Test fun standaloneTrackIsItsOwnGroup() {
        val m = manifest(t("solo", weather = TrackWeather.RAIN), defaults = mapOf(GameTheme.PERSONA_4_REVIVAL to "solo"))
        assertEquals("solo", pick(m, ctx(weather = WeatherType.CLEAR))) // ineligible, but it is the base
        assertEquals("solo", pick(m, ctx(weather = WeatherType.RAIN)))
    }

    @Test fun resolutionIsDeterministic() {
        val c = ctx(weather = WeatherType.RAIN, time = TimeBucket.NIGHT)
        val first = pick(p4Group, c)
        repeat(20) { assertEquals(first, pick(p4Group, c)) }
    }
}
