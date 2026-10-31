package com.example.personabgm.audio

/** Audio-focus events, already mapped from the platform's AUDIOFOCUS_* constants. */
enum class FocusSignal { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

/** What the engine should do in response to a [FocusSignal]. Unset fields mean "no change". */
data class FocusDecision(
    val pause: Boolean = false,
    /** Give focus back to the system (permanent loss). The person must press Play to continue. */
    val abandonFocus: Boolean = false,
    /** Resume playback with the 300 ms fade-in. */
    val resume: Boolean = false,
    val duckFactor: Float? = null,
)

/**
 * Pure state machine for spec 4.3 audio focus behavior:
 *
 *  - GAIN: restore full volume; resume only if a *transient* loss paused playback.
 *  - LOSS: pause and abandon focus; never auto-resume.
 *  - LOSS_TRANSIENT: pause; resume on the next GAIN.
 *  - LOSS_TRANSIENT_CAN_DUCK: keep playing at 20% volume; restore on GAIN.
 *
 * A manual pause during a transient loss cancels the pending auto-resume.
 */
class FocusPolicy(private val duckFactor: Float = FadeMath.DUCK_FACTOR) {

    private var resumeOnGain = false

    fun onSignal(signal: FocusSignal, userWantsPlayback: Boolean): FocusDecision = when (signal) {
        FocusSignal.GAIN -> {
            val resume = resumeOnGain && userWantsPlayback
            resumeOnGain = false
            FocusDecision(resume = resume, duckFactor = 1f)
        }

        FocusSignal.LOSS -> {
            resumeOnGain = false
            FocusDecision(pause = true, abandonFocus = true, duckFactor = 1f)
        }

        FocusSignal.LOSS_TRANSIENT -> {
            resumeOnGain = userWantsPlayback
            FocusDecision(pause = true, duckFactor = 1f)
        }

        FocusSignal.LOSS_TRANSIENT_CAN_DUCK -> FocusDecision(duckFactor = duckFactor)
    }

    /** The person pressed Pause themselves: do not auto-resume after a transient loss. */
    fun onUserPause() {
        resumeOnGain = false
    }

    fun reset() {
        resumeOnGain = false
    }
}
