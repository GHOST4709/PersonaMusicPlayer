package com.example.personabgm.audio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.personabgm.domain.model.AppSettings
import com.example.personabgm.domain.model.CrossfadeCurve
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.Track
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Owns the two ExoPlayers and everything that must stay consistent between them (spec 4.1 to 4.5):
 * crossfades, the master gain (user volume x duck x resume ramp), audio focus, Manual Override.
 *
 * Threading: everything runs on the main thread (the players' application looper). Switches are
 * serialized through [switchRequests]; a CONFLATED channel keeps only the latest request, which is
 * exactly the "latest target wins, start after the current fade" rule of spec 4.2.
 *
 * What AUTO plays is decided by [autoTarget]. In Phase 3 it is the active theme's default track;
 * Phase 4 replaces it with the full context resolver (zone, weather, time of day).
 */
@OptIn(UnstableApi::class)
class CrossfadeEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mediaCache: MediaCache,
    private val settingsFlow: Flow<AppSettings>,
    private val manifestFlow: Flow<Manifest?>,
    private val commands: Flow<PlaybackCommand>,
    private val publishState: (PlaybackUiState) -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : PlayerCommandHandler {

    // ---- players ----
    private val playerA: ExoPlayer = createPlayer()
    private val playerB: ExoPlayer = createPlayer()

    /** The player currently producing the audible track. Swaps with the other one after each crossfade. */
    var activePlayer: ExoPlayer = playerA
        private set
    private var inactivePlayer: ExoPlayer = playerB

    /** Called once after a crossfade completes so the session can be rebound to the new active player. */
    var onActivePlayerChanged: ((ExoPlayer) -> Unit)? = null

    val isPlayingNow: Boolean get() = currentTrack != null && activePlayer.playWhenReady

    // ---- gains (spec 4.2) ----
    private var activeGain = 1f
    private var inactiveGain = 0f
    private var userVolume = 1f
    private var duck = 1f
    private var resumeRamp = 1f
    private var crossfadeCurve = CrossfadeCurve.EQUAL_POWER
    private var crossfadeMs = FadeMath.DEFAULT_CROSSFADE_MS

    // ---- playback intent ----
    private var theme: GameTheme? = null
    private var manifest: Manifest? = null
    private var currentTrack: Track? = null
    private var desiredTrackId: String? = null
    private var mode = PlaybackMode.AUTO
    private var manualTrack: Track? = null
    private var wantsPlayback = false
    private var focusPaused = false
    private var isCrossfading = false
    private var error: String? = null
    private var playAttempts = 0
    private val failedOnce = HashSet<String>()

    private val navigator = OverrideNavigator()
    private val focusPolicy = FocusPolicy()
    private val focus = AudioFocusController(context) { signal -> onFocusSignal(signal) }
    private val switchRequests = Channel<Track>(Channel.CONFLATED)
    private var rampJob: Job? = null
    private var recoveryJob: Job? = null

    init {
        playerA.addListener(PlayerEvents(playerA))
        playerB.addListener(PlayerEvents(playerB))
    }

    fun start() {
        scope.launch { settingsFlow.collect { onSettings(it) } }
        scope.launch { manifestFlow.collect { onManifest(it) } }
        scope.launch { commands.collect { guarded("command $it") { handle(it) } } }
        scope.launch {
            for (track in switchRequests) {
                guarded("switch to ${track.id}") { performSwitch(track) }
            }
        }
    }

    /**
     * A failure inside one command or switch must never take the whole service down: log it, drop
     * any half-finished crossfade state, and keep the current track playing.
     */
    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Playback engine failed during $what", e)
            isCrossfading = false
            desiredTrackId = currentTrack?.id
            error = "Something went wrong with playback. Try again."
            publish()
        }
    }

    fun release() {
        rampJob?.cancel()
        recoveryJob?.cancel()
        focus.abandon()
        playerA.release()
        playerB.release()
    }

    // =====================================================================================
    // Inputs
    // =====================================================================================

    private fun onSettings(settings: AppSettings) {
        userVolume = settings.globalVolume
        crossfadeCurve = settings.crossfadeCurve
        crossfadeMs = settings.crossfadeDurationMs.toLong()
        applyVolumes()
        if (theme != settings.theme) {
            theme = settings.theme
            onAutoContextChanged()
        }
    }

    private fun onManifest(newManifest: Manifest?) {
        manifest = newManifest
        onAutoContextChanged()
    }

    private fun handle(command: PlaybackCommand) {
        when (command) {
            PlaybackCommand.Play -> play()
            PlaybackCommand.Pause -> pause()
            PlaybackCommand.Next -> onNext()
            PlaybackCommand.Previous -> onPrevious()
            PlaybackCommand.ResumeAuto -> resumeAuto()
            is PlaybackCommand.PlayTrack -> manifest?.trackById(command.trackId)?.let { playTrack(it) }
        }
    }

    // PlayerCommandHandler: the media session (notification, Bluetooth, car keys) lands here.
    override fun onPlay() = play()
    override fun onPause() = pause()
    override fun onNext() = step(forward = true)
    override fun onPrevious() = step(forward = false)

    // =====================================================================================
    // Selection: what should be playing
    // =====================================================================================

    private fun autoTarget(): Track? {
        val activeTheme = theme ?: return null
        return manifest?.themeDefault(activeTheme)
    }

    private fun desiredTrack(): Track? = if (mode == PlaybackMode.MANUAL) manualTrack else autoTarget()

    /** AUTO follows context. Nothing is loaded until the person has pressed Play at least once. */
    private fun onAutoContextChanged() {
        if (mode != PlaybackMode.AUTO) return
        if (currentTrack == null && !wantsPlayback) return
        autoTarget()?.let { requestTrack(it) }
    }

    private fun requestTrack(track: Track) {
        if (track.id == desiredTrackId) return // already current, queued, or in flight
        desiredTrackId = track.id
        error = null
        switchRequests.trySend(track)
    }

    private fun playTrack(track: Track) {
        failedOnce.clear()
        navigator.recordManualPlay(currentTrack?.id, track.id)
        enterManual(track)
    }

    private fun step(forward: Boolean) {
        failedOnce.clear()
        val m = manifest ?: return
        val pool = theme?.let { m.tracksFor(it) }.orEmpty()
        val target: Track? = if (forward) {
            navigator.next(currentTrack?.id, pool)
        } else {
            navigator.previous(currentTrack?.id, pool) { id -> m.trackById(id) }
        }
        if (target == null) return
        enterManual(target)
    }

    private fun enterManual(track: Track) {
        mode = PlaybackMode.MANUAL
        manualTrack = track
        requestTrack(track)
        // From a paused or idle state, play() loads the chosen track directly (no crossfade from silence).
        if (!wantsPlayback) play() else publish()
    }

    private fun resumeAuto() {
        failedOnce.clear()
        mode = PlaybackMode.AUTO
        manualTrack = null
        navigator.clear()
        autoTarget()?.let { requestTrack(it) }
        publish()
    }

    // =====================================================================================
    // Transport
    // =====================================================================================

    private fun play() {
        failedOnce.clear()
        if (!focus.request()) {
            error = "Another app is using audio right now."
            publish()
            return
        }
        val wasWanting = wantsPlayback
        wantsPlayback = true
        focusPaused = false
        focusPolicy.reset()
        duck = 1f

        val loaded = currentTrack
        val desired = desiredTrack()
        when {
            loaded == null -> {
                if (desired == null) {
                    wantsPlayback = false
                    focus.abandon()
                    error = "No track is available yet."
                    publish()
                } else {
                    loadImmediately(desired)
                }
            }
            // Paused on one track but a different one was chosen meanwhile: load it directly.
            !wasWanting && !isCrossfading && desired != null && desired.id != loaded.id -> loadImmediately(desired)
            else -> {
                activePlayer.playWhenReady = true
                if (isCrossfading) inactivePlayer.playWhenReady = true
                error = null
                beginResumeRamp()
                publish()
            }
        }
    }

    private fun pause() {
        wantsPlayback = false
        focusPaused = false
        focusPolicy.onUserPause()
        activePlayer.playWhenReady = false
        inactivePlayer.playWhenReady = false
        focus.abandon()
        publish()
    }

    // =====================================================================================
    // Switching tracks
    // =====================================================================================

    private suspend fun performSwitch(track: Track) {
        if (track.id == currentTrack?.id) {
            publish()
            return
        }
        if (currentTrack == null || !wantsPlayback) loadImmediately(track) else crossfadeTo(track)
    }

    /** Loads a track into the active player with no crossfade (first play, or while paused). */
    private fun loadImmediately(track: Track) {
        inactivePlayer.stop()
        inactivePlayer.clearMediaItems()
        inactiveGain = 0f
        activeGain = 1f

        val player = activePlayer
        player.setMediaItem(track.toMediaItem())
        player.prepare()
        player.playWhenReady = wantsPlayback && !focusPaused

        currentTrack = track
        desiredTrackId = track.id
        if (wantsPlayback) beginResumeRamp() else applyVolumes()
        publish()
    }

    /** Spec 4.2: prepare silently, wait for READY, fade over [crossfadeMs], swap, rebind the session. */
    private suspend fun crossfadeTo(track: Track) {
        val outgoing = activePlayer
        val incoming = inactivePlayer

        isCrossfading = true
        error = null
        publish()

        incoming.volume = 0f
        inactiveGain = 0f
        incoming.setMediaItem(track.toMediaItem())
        incoming.prepare()
        incoming.playWhenReady = !focusPaused

        if (!awaitReady(incoming)) {
            // Never leave a gap: keep the current track and (once) try again shortly.
            incoming.stop()
            incoming.clearMediaItems()
            isCrossfading = false
            desiredTrackId = currentTrack?.id
            error = "Couldn't load \"${track.title}\". Staying on the current track."
            publish()
            scheduleRetryOnce(track)
            return
        }
        failedOnce.remove(track.id)

        runTimeline(crossfadeMs) { t ->
            val (incomingGain, outgoingGain) = FadeMath.crossfadeGains(t, crossfadeCurve)
            inactiveGain = incomingGain
            activeGain = outgoingGain
            applyVolumes()
        }

        outgoing.stop()
        outgoing.clearMediaItems()
        activePlayer = incoming
        inactivePlayer = outgoing
        activeGain = 1f
        inactiveGain = 0f
        currentTrack = track
        isCrossfading = false
        applyVolumes()
        onActivePlayerChanged?.invoke(incoming) // rebind the session once, after the fade (spec 4.1)
        publish()
    }

    private suspend fun awaitReady(player: ExoPlayer): Boolean {
        val start = now()
        while (now() - start < READY_TIMEOUT_MS) {
            if (player.playerError != null) return false
            if (player.playbackState == Player.STATE_READY) return true
            delay(POLL_MS)
        }
        return false
    }

    private fun scheduleRetryOnce(track: Track) {
        if (!failedOnce.add(track.id)) return // already retried once
        scope.launch {
            delay(RETRY_DELAY_MS)
            if (desiredTrack()?.id == track.id) requestTrack(track)
        }
    }

    // =====================================================================================
    // Volume
    // =====================================================================================

    private fun applyVolumes() {
        val master = FadeMath.masterGain(userVolume, duck, resumeRamp)
        activePlayer.volume = activeGain * master
        inactivePlayer.volume = inactiveGain * master
    }

    /** 300 ms fade-in on resume (spec 4.2) so Bluetooth devices do not pop. */
    private fun beginResumeRamp() {
        rampJob?.cancel()
        resumeRamp = 0f
        applyVolumes()
        rampJob = scope.launch {
            runTimeline(FadeMath.RESUME_RAMP_MS) { t ->
                resumeRamp = t
                applyVolumes()
            }
        }
    }

    private suspend fun runTimeline(durationMs: Long, onProgress: (Float) -> Unit) {
        val start = now()
        while (true) {
            val progress = FadeMath.progress(now() - start, durationMs)
            onProgress(progress)
            if (progress >= 1f) return
            delay(FRAME_MS)
        }
    }

    // =====================================================================================
    // Audio focus and errors
    // =====================================================================================

    private fun onFocusSignal(signal: FocusSignal) {
        val decision = focusPolicy.onSignal(signal, userWantsPlayback = wantsPlayback)

        decision.duckFactor?.let {
            duck = it
            applyVolumes()
        }
        if (decision.pause) {
            activePlayer.playWhenReady = false
            inactivePlayer.playWhenReady = false
            if (decision.abandonFocus) {
                wantsPlayback = false
                focusPaused = false
                focus.abandon()
            } else {
                focusPaused = wantsPlayback
            }
            publish()
        }
        if (decision.resume) {
            focusPaused = false
            activePlayer.playWhenReady = true
            if (isCrossfading) inactivePlayer.playWhenReady = true
            beginResumeRamp()
            publish()
        }
    }

    private fun handleActiveError() {
        playAttempts++
        if (playAttempts > MAX_PLAY_RETRIES) {
            error = "Couldn't play this track. Check your connection and try again."
            publish()
            return
        }
        error = "Playback problem. Retrying..."
        publish()
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            delay(RETRY_DELAY_MS * playAttempts)
            if (activePlayer.playerError != null) activePlayer.prepare()
        }
    }

    private inner class PlayerEvents(private val player: ExoPlayer) : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (player !== activePlayer) return
            if (playbackState == Player.STATE_READY && playAttempts > 0) {
                playAttempts = 0
                error = null
            }
            publish()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Headphones unplugged / Bluetooth disconnected (spec 4.3): pause the pair and cancel nothing else.
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                pause()
            } else if (player === activePlayer) {
                publish()
            }
        }

        override fun onPlayerError(playbackException: PlaybackException) {
            if (player === activePlayer) handleActiveError()
        }
    }

    // =====================================================================================
    // Output
    // =====================================================================================

    private fun publish() {
        val player = activePlayer
        publishState(
            PlaybackUiState(
                track = currentTrack,
                isPlaying = currentTrack != null && player.playWhenReady,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                isCrossfading = isCrossfading,
                mode = mode,
                error = error,
            ),
        )
    }

    private fun createPlayer(): ExoPlayer {
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(mediaCache.dataSourceFactory))
            // handleAudioFocus = false: AudioFocusController is the only focus owner (see its docs).
            .setAudioAttributes(attributes, false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE // BGM loops (spec 4.6)
                volume = 0f
            }
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setCustomCacheKey(id) // stable cache key (spec 4.4)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build(),
        )
        .build()

    private companion object {
        const val TAG = "CrossfadeEngine"
        const val FRAME_MS = 16L
        const val POLL_MS = 50L
        const val READY_TIMEOUT_MS = 8_000L
        const val RETRY_DELAY_MS = 3_000L
        const val MAX_PLAY_RETRIES = 3
    }
}
