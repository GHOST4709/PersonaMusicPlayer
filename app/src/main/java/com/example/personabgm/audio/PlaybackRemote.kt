package com.example.personabgm.audio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
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
     * For events with no UI on screen (a geofence transition). Uses `startForegroundService`, which Android
     * allows from a geofence receiver; the service posts a notification within seconds of starting (see
     * `PlaybackService.promoteToForeground`). If the OS refuses, fall back to a tap-to-resume notification
     * (spec 4.1). Returns whether the service was started.
     */
    fun sendFromBackground(command: PlaybackCommand): Boolean {
        commandChannel.trySend(command)
        val intent = Intent(context, PlaybackService::class.java)
            .putExtra(PlaybackService.EXTRA_FOREGROUND_START, true)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException.
            Log.w(TAG, "Could not start the playback service from the background", e)
            drainQueue()
            ResumeNotification.show(context)
            false
        }
    }

    /**
     * Started from the foreground UI, which is always allowed. Media3 promotes the service to a
     * foreground service itself once playback begins.
     */
    private fun startService() {
        try {
            context.startService(Intent(context, PlaybackService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not start the playback service", e)
            drainQueue()
        }
    }

    /** Do not leave commands queued for a service that is not coming. */
    private fun drainQueue() {
        while (commandChannel.tryReceive().isSuccess) { /* discard */ }
    }

    private companion object {
        const val TAG = "PlaybackRemote"
    }
}
