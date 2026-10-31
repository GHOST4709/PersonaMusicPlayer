package com.example.personabgm.audio

import com.example.personabgm.domain.model.CrossfadeCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FadeMathTest {

    private fun near(expected: Float, actual: Float, eps: Float = 1e-4f) =
        assertTrue("expected $expected but was $actual", abs(expected - actual) <= eps)

    @Test
    fun endpointsAreExactForBothCurves() {
        CrossfadeCurve.entries.forEach { curve ->
            assertEquals(0f to 1f, FadeMath.crossfadeGains(0f, curve))
            assertEquals(1f to 0f, FadeMath.crossfadeGains(1f, curve))
        }
    }

    @Test
    fun outOfRangeProgressIsClamped() {
        assertEquals(0f to 1f, FadeMath.crossfadeGains(-3f, CrossfadeCurve.EQUAL_POWER))
        assertEquals(1f to 0f, FadeMath.crossfadeGains(7f, CrossfadeCurve.LINEAR))
    }

    @Test
    fun linearGainsSumToOneAndMidpointIsHalf() {
        for (i in 0..100) {
            val (inc, out) = FadeMath.crossfadeGains(i / 100f, CrossfadeCurve.LINEAR)
            near(1f, inc + out)
        }
        val (inc, out) = FadeMath.crossfadeGains(0.5f, CrossfadeCurve.LINEAR)
        near(0.5f, inc); near(0.5f, out)
    }

    @Test
    fun equalPowerKeepsConstantPowerThroughoutTheFade() {
        // Sum of squares stays at 1: no loudness dip at the midpoint (the reason it is the default).
        for (i in 0..100) {
            val (inc, out) = FadeMath.crossfadeGains(i / 100f, CrossfadeCurve.EQUAL_POWER)
            near(1f, inc * inc + out * out)
        }
        val (inc, out) = FadeMath.crossfadeGains(0.5f, CrossfadeCurve.EQUAL_POWER)
        near(0.7071f, inc, 1e-3f); near(0.7071f, out, 1e-3f)
    }

    @Test
    fun linearFadeHasAMidpointLoudnessDipThatEqualPowerAvoids() {
        val (li, lo) = FadeMath.crossfadeGains(0.5f, CrossfadeCurve.LINEAR)
        val (ei, eo) = FadeMath.crossfadeGains(0.5f, CrossfadeCurve.EQUAL_POWER)
        assertTrue(li * li + lo * lo < 0.6f)
        assertTrue(ei * ei + eo * eo > 0.99f)
    }

    @Test
    fun gainsAreMonotonic() {
        CrossfadeCurve.entries.forEach { curve ->
            var prevIn = -1f; var prevOut = 2f
            for (i in 0..200) {
                val (inc, out) = FadeMath.crossfadeGains(i / 200f, curve)
                assertTrue(inc >= prevIn); assertTrue(out <= prevOut)
                prevIn = inc; prevOut = out
            }
        }
    }

    @Test
    fun progressIsClampedAndZeroDurationIsInstant() {
        near(0f, FadeMath.progress(0, 2500))
        near(0.5f, FadeMath.progress(1250, 2500))
        near(1f, FadeMath.progress(9999, 2500))
        near(0f, FadeMath.progress(-50, 2500))
        near(1f, FadeMath.progress(0, 0))
        near(1f, FadeMath.progress(10, -5))
    }

    @Test
    fun masterGainMultipliesAndClamps() {
        near(1f, FadeMath.masterGain(1f, 1f, 1f))
        near(0.2f, FadeMath.masterGain(1f, FadeMath.DUCK_FACTOR, 1f))
        near(0.1f, FadeMath.masterGain(0.5f, 0.2f, 1f))
        near(0f, FadeMath.masterGain(1f, 1f, 0f))
        near(1f, FadeMath.masterGain(5f, 9f, 3f))
        near(0f, FadeMath.masterGain(-1f, 1f, 1f))
    }

    @Test
    fun specConstants() {
        assertEquals(2500L, FadeMath.DEFAULT_CROSSFADE_MS)
        assertEquals(300L, FadeMath.RESUME_RAMP_MS)
        near(0.2f, FadeMath.DUCK_FACTOR)
    }
}
