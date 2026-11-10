package com.example.personabgm.domain.resolver

import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.MusicScenario
import com.example.personabgm.domain.model.ScenarioScope
import com.example.personabgm.domain.model.ScenarioSceneCondition
import com.example.personabgm.domain.model.ScenarioTimeCondition
import com.example.personabgm.domain.model.ScenarioWeatherCondition
import com.example.personabgm.domain.model.PlaybackContext
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import com.example.personabgm.domain.model.WeatherType

/**
 * Resolves matching user scenarios before using the established zone-assignment/theme-default fallback.
 * Persona-specific rules outrank All/global rules; priority orders rules within the same scope.
 * Conditions are conjunctive. Track variants still use the existing weather (+2) and time (+1) score.
 * This remains a pure, deterministic function and never needs to send context to the cloud.
 */
object ContextResolver {

    fun resolve(
        context: PlaybackContext,
        manifest: Manifest,
        scenarios: List<MusicScenario> = emptyList(),
    ): Track? {
        // Persona-specific rules win over global rules. Priority only orders rules inside one scope.
        val matchingScenarios = scenarios.asSequence()
            .filter { it.enabled && it.matches(context) }
            .sortedWith(
                compareByDescending<MusicScenario> { it.scope.themeOrNull() == context.theme }
                    .thenByDescending { it.priority }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.id },
            )

        for (scenario in matchingScenarios) {
            val base = scenario.trackIds.asSequence()
                .mapNotNull(manifest::trackById)
                .firstOrNull { it.gameTheme == context.theme }
            if (base != null) return resolveVariants(base, context, manifest)
        }

        // Preserve the existing zone-assignment and theme-default fallback exactly when no rule matches.
        val fallback = context.activeZone?.assignedTrackId?.let { manifest.trackById(it) }
            ?: manifest.themeDefault(context.theme)
            ?: return null
        return resolveVariants(fallback, context, manifest)
    }

    private fun MusicScenario.matches(context: PlaybackContext): Boolean =
        (scope == ScenarioScope.ALL || scope.themeOrNull() == context.theme) &&
            (scene == ScenarioSceneCondition.ANY ||
                (scene == ScenarioSceneCondition.NORMAL && context.playbackScene == com.example.personabgm.domain.model.PlaybackScene.NORMAL) ||
                (scene == ScenarioSceneCondition.BATTLE && context.playbackScene == com.example.personabgm.domain.model.PlaybackScene.BATTLE)) &&
            (time == ScenarioTimeCondition.ANY ||
                (time == ScenarioTimeCondition.DAY && context.time == TimeBucket.DAY) ||
                (time == ScenarioTimeCondition.NIGHT && context.time == TimeBucket.NIGHT)) &&
            (weather == ScenarioWeatherCondition.ANY ||
                (weather == ScenarioWeatherCondition.CLEAR && context.weather == WeatherType.CLEAR) ||
                (weather == ScenarioWeatherCondition.RAIN && context.weather == WeatherType.RAIN) ||
                (weather == ScenarioWeatherCondition.SNOW && context.weather == WeatherType.SNOW)) &&
            (zoneId == null || zoneId == context.activeZone?.id)

    private fun resolveVariants(base: Track, context: PlaybackContext, manifest: Manifest): Track =
        manifest.variantsOf(base)
            .filter { it.weather.accepts(context.weather) && it.timeOfDay.accepts(context.time) }
            .maxWithOrNull(
                compareBy<Track>({ score(it, context) }, { it.id == base.id })
                    .thenByDescending { it.id },
            )
            ?: base

    private fun score(track: Track, context: PlaybackContext): Int =
        (if (track.weather.matchesExactly(context.weather)) WEATHER_POINTS else 0) +
            (if (track.timeOfDay.matchesExactly(context.time)) TIME_POINTS else 0)

    // Weather outranks time of day: rain is the stronger signal (spec 5.3).
    private const val WEATHER_POINTS = 2
    private const val TIME_POINTS = 1
}

internal fun TrackWeather.accepts(current: WeatherType): Boolean = this == TrackWeather.ALL || matchesExactly(current)

internal fun TrackWeather.matchesExactly(current: WeatherType): Boolean = when (this) {
    TrackWeather.CLEAR -> current == WeatherType.CLEAR
    TrackWeather.RAIN -> current == WeatherType.RAIN
    TrackWeather.SNOW -> current == WeatherType.SNOW
    TrackWeather.ALL -> false
}

internal fun TrackTime.accepts(current: TimeBucket): Boolean = this == TrackTime.ALL || matchesExactly(current)

internal fun TrackTime.matchesExactly(current: TimeBucket): Boolean = when (this) {
    TrackTime.DAY -> current == TimeBucket.DAY
    TrackTime.NIGHT -> current == TimeBucket.NIGHT
    TrackTime.ALL -> false
}
