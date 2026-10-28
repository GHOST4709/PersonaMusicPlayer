package com.example.personabgm.domain.model

/** Active visual/audio theme. Names match the `game_theme` values in the track manifest. */
enum class GameTheme {
    PERSONA_3_RELOAD,
    PERSONA_4_REVIVAL,
    PERSONA_5_ROYAL;

    companion object {
        val Default: GameTheme = PERSONA_3_RELOAD

        /** Unknown or missing values fall back to [Default] so old/new data never crashes the app. */
        fun fromNameOrDefault(name: String?): GameTheme =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
