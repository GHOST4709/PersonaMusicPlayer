package com.example.personabgm.audio

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The seam between the UI and the playback service. The UI sends [PlaybackCommand]s and reads
 * [state]; the service consumes [commands] and publishes state. Commands are queued, so one sent
 * before the service has finished starting is not lost.
 *
 * UI and service share a process, so this is a plain singleton rather than a bound MediaController.
 * The MediaSession still serves everything external (notification, Bluetooth, car).
 */
@Singleton
class PlaybackRemote @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val commandChannel = Channel<PlaybackCommand>(Channel.UNLIMITED)
    val commands: Flow<PlaybackCommand> = commandChannel.receiveAsFlow()

    private val _state = MutableStateFlow(PlaybackUiState.Idle)
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun send(command: PlaybackCommand) {
        commandChannel.trySend(command)
        startService()
    }

    internal fun publish(state: PlaybackUiState) {
        _state.value = state
    }

    internal fun onServiceStopped() {
        _state.value = PlaybackUiState.Idle
    }

    /**
     * Started from the foreground UI, which is always allowed. Media3 promotes the service to a
     * foreground service itself once playback begins. (Background starts, such as geofence events,
     * arrive in Phase 4 and use the geofence exemption.)
     */
    private fun startService() {
        try {
            context.startService(Intent(context, PlaybackService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not start the playback service", e)
            // Do not leave the command queued for a service that is not coming.
            while (commandChannel.tryReceive().isSuccess) { /* drain */ }
        }
    }

    private companion object {
        const val TAG = "PlaybackRemote"
    }
}
