package com.example.personabgm.audio

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/** What the media session (notification, Bluetooth, car) may ask of the engine. */
interface PlayerCommandHandler {
    fun onPlay()
    fun onPause()
    fun onNext()
    fun onPrevious()
}

/**
 * The single "logical player" the MediaSession sees (spec 4.1). It wraps whichever ExoPlayer is
 * currently active, but routes transport controls to the engine so both players, audio focus, the
 * resume fade, and Manual Override are handled in one place.
 *
 * The engine recreates this wrapper around the new active player once a crossfade completes.
 */
class CrossfadePlayer(
    player: Player,
    private val handler: PlayerCommandHandler,
) : ForwardingPlayer(player) {

    override fun play() = handler.onPlay()

    override fun pause() = handler.onPause()

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) handler.onPlay() else handler.onPause()
    }

    // A "stop" from a controller (for example dismissing the notification) is treated as a pause.
    override fun stop() = handler.onPause()

    override fun seekToNext() = handler.onNext()
    override fun seekToNextMediaItem() = handler.onNext()
    override fun seekToPrevious() = handler.onPrevious()
    override fun seekToPreviousMediaItem() = handler.onPrevious()

    // The underlying players hold a single item, so advertise Next/Previous ourselves.
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true
}
