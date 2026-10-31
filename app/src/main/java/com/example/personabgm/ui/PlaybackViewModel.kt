package com.example.personabgm.ui

import androidx.lifecycle.ViewModel
import com.example.personabgm.audio.PlaybackCommand
import com.example.personabgm.audio.PlaybackRemote
import com.example.personabgm.audio.PlaybackUiState
import com.example.personabgm.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Thin UI adapter over [PlaybackRemote]: state in, commands out. */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val remote: PlaybackRemote,
) : ViewModel() {

    val state: StateFlow<PlaybackUiState> = remote.state

    fun togglePlayPause() {
        remote.send(if (state.value.isPlaying) PlaybackCommand.Pause else PlaybackCommand.Play)
    }

    fun next() = remote.send(PlaybackCommand.Next)
    fun previous() = remote.send(PlaybackCommand.Previous)
    fun resumeAuto() = remote.send(PlaybackCommand.ResumeAuto)
    fun playTrack(track: Track) = remote.send(PlaybackCommand.PlayTrack(track.id))
}
