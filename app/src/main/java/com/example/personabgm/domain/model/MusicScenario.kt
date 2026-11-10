package com.example.personabgm.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** The four independently managed scenario collections exposed by the UI. */
@Serializable
enum class ScenarioScope(val label: String) {
    ALL("All"),
    PERSONA_3_RELOADED("Persona 3 Reloaded"),
    PERSONA_4_REVIVAL("Persona 4 Revival"),
    PERSONA_5_ROYAL("Persona 5 Royal");

    fun themeOrNull(): GameTheme? = when (this) {
        ALL -> null
        PERSONA_3_RELOADED -> GameTheme.PERSONA_3_RELOAD
        PERSONA_4_REVIVAL -> GameTheme.PERSONA_4_REVIVAL
        PERSONA_5_ROYAL -> GameTheme.PERSONA_5_ROYAL
    }

    companion object {
        fun forTheme(theme: GameTheme): ScenarioScope = when (theme) {
            GameTheme.PERSONA_3_RELOAD -> PERSONA_3_RELOADED
            GameTheme.PERSONA_4_REVIVAL -> PERSONA_4_REVIVAL
            GameTheme.PERSONA_5_ROYAL -> PERSONA_5_ROYAL
        }
    }
}

@Serializable
enum class ScenarioSceneCondition(val label: String) {
    ANY("Any scene"), NORMAL("Normal Mode"), BATTLE("Battle Mode")
}

@Serializable
enum class ScenarioTimeCondition(val label: String) {
    ANY("Any time"), DAY("Daytime"), NIGHT("Night")
}

@Serializable
enum class ScenarioWeatherCondition(val label: String) {
    ANY("Any weather"), CLEAR("Clear"), RAIN("Rain"), SNOW("Snow")
}

/**
 * A local rule. Conditions are combined with AND. `trackIds` are ordered: the first available
 * track for the active Persona is used as the rule's base track, and the existing variant resolver
 * still chooses the best day/night/weather variant within its group.
 */
@Serializable
data class MusicScenario(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val scope: ScenarioScope,
    val enabled: Boolean = true,
    val scene: ScenarioSceneCondition = ScenarioSceneCondition.ANY,
    val time: ScenarioTimeCondition = ScenarioTimeCondition.ANY,
    val weather: ScenarioWeatherCondition = ScenarioWeatherCondition.ANY,
    val zoneId: String? = null,
    val trackIds: List<String> = emptyList(),
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
