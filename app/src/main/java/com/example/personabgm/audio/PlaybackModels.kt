package com.example.personabgm.audio

import com.example.personabgm.domain.model.ContextInfo
import com.example.personabgm.domain.model.Track

/** AUTO follows the context (Phase 3: the active theme's default track). MANUAL is a person's override. */
enum class PlaybackMode { AUTO, MANUAL }

/** Everything the UI needs to know about playback. Published by the engine, read by view models. */
data class PlaybackUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isCrossfading: Boolean = false,
    val mode: PlaybackMode = PlaybackMode.AUTO,
    val error: String? = null,
    /** Why AUTO would play what it plays: zone, weather, time of day. Null until the first context arrives. */
    val context: ContextInfo? = null,
) {
    companion object {
        val Idle = PlaybackUiState()
    }
}

/** Commands from the UI (and, later, the context engine) to the playback service. */
sealed interface PlaybackCommand {
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data object ResumeAuto : PlaybackCommand

    /** Sent by the geofence receiver: resume if (and only if) the person was listening before the service died. */
    data object AutoResume : PlaybackCommand
    data class PlayTrack(val trackId: String) : PlaybackCommand
}
