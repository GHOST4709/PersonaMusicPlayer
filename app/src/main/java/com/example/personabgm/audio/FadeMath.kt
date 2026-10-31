package com.example.personabgm.audio

import com.example.personabgm.domain.model.CrossfadeCurve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pure gain math for crossfades, resume ramps, and ducking (spec 4.2, 4.3). No Android types. */
object FadeMath {
    /** Spec 4.3: AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK lowers volume to 20%. */
    const val DUCK_FACTOR = 0.2f

    /** Spec 4.2: default crossfade length. */
    const val DEFAULT_CROSSFADE_MS = 2500L

    /** Spec 4.2: fade-in applied when playback is manually resumed (avoids Bluetooth popping). */
    const val RESUME_RAMP_MS = 300L

    /** 0..1 progress of a timeline; a zero or negative duration is instantly complete. */
    fun progress(elapsedMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 1f else (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    /**
     * Gains during a crossfade at progress [t] in 0..1, as (incoming, outgoing).
     * Endpoints are exact: (0, 1) at t=0 and (1, 0) at t=1, so the swap leaves no residual gain.
     */
    fun crossfadeGains(t: Float, curve: CrossfadeCurve): Pair<Float, Float> {
        val x = t.coerceIn(0f, 1f)
        if (x <= 0f) return 0f to 1f
        if (x >= 1f) return 1f to 0f
        return when (curve) {
            CrossfadeCurve.LINEAR -> x to (1f - x)
            CrossfadeCurve.EQUAL_POWER -> sin(x * (PI / 2)).toFloat() to cos(x * (PI / 2)).toFloat()
        }
    }

    /** Master gain = user volume x duck factor x resume ramp, each clamped to 0..1. */
    fun masterGain(userVolume: Float, duck: Float, resumeRamp: Float): Float =
        userVolume.coerceIn(0f, 1f) * duck.coerceIn(0f, 1f) * resumeRamp.coerceIn(0f, 1f)
}
