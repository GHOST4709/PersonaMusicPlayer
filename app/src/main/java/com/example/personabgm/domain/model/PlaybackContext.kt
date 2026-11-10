package com.example.personabgm.domain.model

/** Everything the resolver needs to pick a track (spec 5.3). */
data class PlaybackContext(
    val activeZone: Zone?,
    val theme: GameTheme,
    val weather: WeatherType,
    val time: TimeBucket,
    val playbackScene: PlaybackScene = PlaybackScene.NORMAL,
)

/** The parts of the context worth showing to the person (Home chips) and logging. */
data class ContextInfo(
    val zoneId: String?,
    val zoneName: String?,
    val weather: WeatherType,
    val time: TimeBucket,
    val theme: GameTheme,
)

/** What AUTO mode should be playing right now, and why. */
data class AutoContext(
    val target: Track?,
    val info: ContextInfo,
)
