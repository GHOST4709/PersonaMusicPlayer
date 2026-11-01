package com.example.personabgm.domain.resolver

import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.PlaybackContext
import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import com.example.personabgm.domain.model.WeatherType

/**
 * Chooses what AUTO mode plays (spec 5.3). Weather and time of day are *variant selectors*, not
 * competing priority tiers (the v1 cascade could never reach the zone track, because time of day is
 * always either Day or Night).
 *
 * Stage 1, base track: the active zone's assigned track, else the active theme's roaming default.
 * Stage 2, variant: among the base track's variant group, keep tracks whose weather and time tags
 * accept the current conditions, then take the best score (+2 exact weather, +1 exact time).
 * Ties go to the base track, then to the lowest id. If nothing is eligible, the base track plays:
 * a zone is never silent.
 *
 * A pure function: the same inputs always give the same track.
 */
object ContextResolver {

    fun resolve(context: PlaybackContext, manifest: Manifest): Track? {
        val base = context.activeZone?.assignedTrackId?.let { manifest.trackById(it) }
            ?: manifest.themeDefault(context.theme)
            ?: return null

        return manifest.variantsOf(base)
            .filter { it.weather.accepts(context.weather) && it.timeOfDay.accepts(context.time) }
            .maxWithOrNull(
                compareBy<Track>({ score(it, context) }, { it.id == base.id })
                    .thenByDescending { it.id },
            )
            ?: base
    }

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
