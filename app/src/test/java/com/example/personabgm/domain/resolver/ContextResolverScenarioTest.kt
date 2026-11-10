package com.example.personabgm.domain.resolver

import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.MusicScenario
import com.example.personabgm.domain.model.PlaybackContext
import com.example.personabgm.domain.model.PlaybackScene
import com.example.personabgm.domain.model.ScenarioSceneCondition
import com.example.personabgm.domain.model.ScenarioScope
import com.example.personabgm.domain.model.ScenarioTimeCondition
import com.example.personabgm.domain.model.ScenarioWeatherCondition
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import com.example.personabgm.domain.model.WeatherType
import com.example.personabgm.domain.model.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextResolverScenarioTest {
    private fun track(id: String, theme: GameTheme = GameTheme.PERSONA_3_RELOAD) = Track(
        id = id,
        gameTheme = theme,
        title = id,
        artist = "Test artist",
        timeOfDay = TrackTime.ALL,
        weather = TrackWeather.ALL,
        variantGroup = null,
        streamUrl = "https://example.invalid/$id.mp3",
        sizeBytes = null,
    )

    private val manifest = Manifest(
        version = 1,
        themeDefaults = mapOf(
            GameTheme.PERSONA_3_RELOAD to "p3_default",
            GameTheme.PERSONA_4_REVIVAL to "p4_default",
        ),
        tracks = listOf(
            track("p3_default"), track("p3_global"), track("p3_specific"), track("p3_battle"),
            track("p4_default", GameTheme.PERSONA_4_REVIVAL),
        ),
    )

    private fun context(
        theme: GameTheme = GameTheme.PERSONA_3_RELOAD,
        scene: PlaybackScene = PlaybackScene.NORMAL,
        time: TimeBucket = TimeBucket.DAY,
        weather: WeatherType = WeatherType.CLEAR,
        zone: Zone? = null,
    ) = PlaybackContext(zone, theme, weather, time, scene)

    @Test fun personaSpecificScenarioBeatsGlobalScenarioRegardlessOfGlobalPriority() {
        val scenarios = listOf(
            MusicScenario(name = "Global", scope = ScenarioScope.ALL, trackIds = listOf("p3_global"), priority = 999),
            MusicScenario(name = "P3", scope = ScenarioScope.PERSONA_3_RELOADED, trackIds = listOf("p3_specific"), priority = 0),
        )
        assertEquals("p3_specific", ContextResolver.resolve(context(), manifest, scenarios)?.id)
    }

    @Test fun globalScenarioIsUsedAsFallbackForAnotherPersona() {
        val scenarios = listOf(
            MusicScenario(name = "Global", scope = ScenarioScope.ALL, trackIds = listOf("p4_default")),
        )
        assertEquals(
            "p4_default",
            ContextResolver.resolve(context(theme = GameTheme.PERSONA_4_REVIVAL), manifest, scenarios)?.id,
        )
    }

    @Test fun battleScenarioOnlyMatchesWhenBattleModeIsSelected() {
        val scenarios = listOf(
            MusicScenario(
                name = "Battle",
                scope = ScenarioScope.PERSONA_3_RELOADED,
                scene = ScenarioSceneCondition.BATTLE,
                trackIds = listOf("p3_battle"),
            ),
        )
        assertEquals("p3_default", ContextResolver.resolve(context(scene = PlaybackScene.NORMAL), manifest, scenarios)?.id)
        assertEquals("p3_battle", ContextResolver.resolve(context(scene = PlaybackScene.BATTLE), manifest, scenarios)?.id)
    }

    @Test fun scenarioConditionsAreCombinedWithAnd() {
        val scenarios = listOf(
            MusicScenario(
                name = "Rainy night battle",
                scope = ScenarioScope.PERSONA_3_RELOADED,
                scene = ScenarioSceneCondition.BATTLE,
                time = ScenarioTimeCondition.NIGHT,
                weather = ScenarioWeatherCondition.RAIN,
                zoneId = "z1",
                trackIds = listOf("p3_battle"),
            ),
        )
        val matchingZone = Zone("z1", "Dorm", 1.0, 1.0, 150f, null, true, 0L)
        assertEquals(
            "p3_battle",
            ContextResolver.resolve(
                context(scene = PlaybackScene.BATTLE, time = TimeBucket.NIGHT, weather = WeatherType.RAIN, zone = matchingZone),
                manifest,
                scenarios,
            )?.id,
        )
        assertEquals(
            "p3_default",
            ContextResolver.resolve(
                context(scene = PlaybackScene.BATTLE, time = TimeBucket.NIGHT, weather = WeatherType.CLEAR, zone = matchingZone),
                manifest,
                scenarios,
            )?.id,
        )
    }

    @Test fun disabledScenarioDoesNotOverrideTheExistingResolver() {
        val scenarios = listOf(
            MusicScenario(
                name = "Disabled",
                scope = ScenarioScope.PERSONA_3_RELOADED,
                enabled = false,
                trackIds = listOf("p3_specific"),
            ),
        )
        assertEquals("p3_default", ContextResolver.resolve(context(), manifest, scenarios)?.id)
    }

    @Test fun scenarioPriorityOrdersRulesWithinTheSameScope() {
        val scenarios = listOf(
            MusicScenario(name = "Low", scope = ScenarioScope.PERSONA_3_RELOADED, trackIds = listOf("p3_global"), priority = 10),
            MusicScenario(name = "High", scope = ScenarioScope.PERSONA_3_RELOADED, trackIds = listOf("p3_specific"), priority = 50),
        )
        assertEquals("p3_specific", ContextResolver.resolve(context(), manifest, scenarios)?.id)
    }

    @Test fun aZoneSpecificScenarioOnlyMatchesItsConfiguredZone() {
        val scenario = MusicScenario(
            name = "Dorm battle",
            scope = ScenarioScope.PERSONA_3_RELOADED,
            scene = ScenarioSceneCondition.BATTLE,
            zoneId = "z1",
            trackIds = listOf("p3_battle"),
        )
        val otherZone = Zone("z2", "Mall", 2.0, 2.0, 150f, null, true, 0L)
        assertEquals(
            "p3_default",
            ContextResolver.resolve(context(scene = PlaybackScene.BATTLE, zone = otherZone), manifest, listOf(scenario))?.id,
        )
    }

    @Test fun unmatchedScenarioPreservesAnExistingZoneTrackAssignment() {
        val zoneTrack = track("p3_zone")
        val manifestWithZoneTrack = manifest.copy(tracks = manifest.tracks + zoneTrack)
        val zone = Zone("z1", "Dorm", 1.0, 1.0, 150f, "p3_zone", true, 0L)
        val unrelated = MusicScenario(
            name = "Only in Battle",
            scope = ScenarioScope.PERSONA_3_RELOADED,
            scene = ScenarioSceneCondition.BATTLE,
            trackIds = listOf("p3_battle"),
        )
        assertEquals("p3_zone", ContextResolver.resolve(context(zone = zone), manifestWithZoneTrack, listOf(unrelated))?.id)
    }

}
