package com.example.personabgm.domain.model

/** User-selected playback scene. This is deliberately separate from AUTO/MANUAL playback control. */
enum class PlaybackScene {
    NORMAL,
    BATTLE;

    companion object {
        fun fromNameOrDefault(value: String?): PlaybackScene =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}
