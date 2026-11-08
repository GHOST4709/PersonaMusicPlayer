package com.example.personabgm.domain.model

/** User settings persisted in DataStore (spec 7.3). Defaults match the spec. */
data class AppSettings(
    val theme: GameTheme = GameTheme.Default,
    val globalVolume: Float = 1f,
    val crossfadeDurationMs: Int = 2500,
    val crossfadeCurve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER,
    val weatherOverrideEnabled: Boolean = true,
    val precacheWifiOnly: Boolean = true,
    /** Use the device's own location while the app is open to notice arriving/leaving sooner (spec 5.2). */
    val fastZoneDetection: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val onboardingCompleted: Boolean = false,
)
