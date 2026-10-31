package com.example.personabgm.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * The single owner of audio focus (spec 4.3). Both ExoPlayers run with built-in focus handling OFF,
 * because two focus-holding players in one app would make the incoming player's request cost the
 * outgoing player its focus in the middle of a crossfade.
 *
 * This class only talks to the platform; what to do about each event lives in [FocusPolicy].
 */
class AudioFocusController(
    context: Context,
    private val onSignal: (FocusSignal) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasFocus = false

    private val request: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        // We duck and pause ourselves, so the system must not do it for us.
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(
            { change -> onFocusChange(change) },
            Handler(Looper.getMainLooper()),
        )
        .build()

    /** Returns true if focus is held after the call. Safe to call repeatedly. */
    fun request(): Boolean {
        if (hasFocus) return true
        hasFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    fun abandon() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(request)
        hasFocus = false
    }

    private fun onFocusChange(change: Int) {
        if (change == AudioManager.AUDIOFOCUS_LOSS) hasFocus = false // the system already took it away
        mapFocusChange(change)?.let(onSignal)
    }

    private fun mapFocusChange(change: Int): FocusSignal? = when (change) {
        AudioManager.AUDIOFOCUS_GAIN,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
        -> FocusSignal.GAIN

        AudioManager.AUDIOFOCUS_LOSS -> FocusSignal.LOSS
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusSignal.LOSS_TRANSIENT
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusSignal.LOSS_TRANSIENT_CAN_DUCK
        else -> null
    }
}
