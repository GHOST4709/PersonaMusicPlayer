package com.example.personabgm.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPolicyTest {

    private val policy = FocusPolicy()

    @Test
    fun canDuckLowersToTwentyPercentWithoutPausing() {
        val d = policy.onSignal(FocusSignal.LOSS_TRANSIENT_CAN_DUCK, userWantsPlayback = true)
        assertEquals(0.2f, d.duckFactor)
        assertFalse(d.pause); assertFalse(d.resume); assertFalse(d.abandonFocus)
    }

    @Test
    fun gainAfterDuckRestoresFullVolumeWithoutResumeRamp() {
        policy.onSignal(FocusSignal.LOSS_TRANSIENT_CAN_DUCK, true)
        val d = policy.onSignal(FocusSignal.GAIN, true)
        assertEquals(1f, d.duckFactor)
        assertFalse("ducking never paused, so nothing to resume", d.resume)
    }

    @Test
    fun transientLossPausesAndGainResumesWithRamp() {
        val loss = policy.onSignal(FocusSignal.LOSS_TRANSIENT, true)
        assertTrue(loss.pause); assertFalse(loss.abandonFocus)
        val gain = policy.onSignal(FocusSignal.GAIN, true)
        assertTrue(gain.resume); assertEquals(1f, gain.duckFactor)
    }

    @Test
    fun transientLossWhileAlreadyPausedByUserDoesNotResume() {
        policy.onSignal(FocusSignal.LOSS_TRANSIENT, userWantsPlayback = false)
        assertFalse(policy.onSignal(FocusSignal.GAIN, userWantsPlayback = false).resume)
    }

    @Test
    fun permanentLossPausesAbandonsAndNeverAutoResumes() {
        val loss = policy.onSignal(FocusSignal.LOSS, true)
        assertTrue(loss.pause); assertTrue(loss.abandonFocus)
        assertFalse(policy.onSignal(FocusSignal.GAIN, true).resume)
    }

    @Test
    fun permanentLossAfterTransientCancelsThePendingResume() {
        policy.onSignal(FocusSignal.LOSS_TRANSIENT, true)
        policy.onSignal(FocusSignal.LOSS, true)
        assertFalse(policy.onSignal(FocusSignal.GAIN, true).resume)
    }

    @Test
    fun userPauseDuringTransientLossCancelsAutoResume() {
        policy.onSignal(FocusSignal.LOSS_TRANSIENT, true)
        policy.onUserPause()
        assertFalse(policy.onSignal(FocusSignal.GAIN, true).resume)
    }

    @Test
    fun gainDoesNotResumeIfPlaybackIsNoLongerWanted() {
        policy.onSignal(FocusSignal.LOSS_TRANSIENT, true)
        assertFalse(policy.onSignal(FocusSignal.GAIN, userWantsPlayback = false).resume)
    }

    @Test
    fun transientLossAfterDuckResetsDuckToFull() {
        policy.onSignal(FocusSignal.LOSS_TRANSIENT_CAN_DUCK, true)
        assertEquals(1f, policy.onSignal(FocusSignal.LOSS_TRANSIENT, true).duckFactor)
    }

    @Test
    fun resumeIsOneShot() {
        policy.onSignal(FocusSignal.LOSS_TRANSIENT, true)
        assertTrue(policy.onSignal(FocusSignal.GAIN, true).resume)
        assertFalse(policy.onSignal(FocusSignal.GAIN, true).resume)
    }

    @Test
    fun gainWithNothingPendingOnlyRestoresVolume() {
        val d = policy.onSignal(FocusSignal.GAIN, true)
        assertEquals(1f, d.duckFactor)
        assertFalse(d.pause); assertFalse(d.resume); assertFalse(d.abandonFocus)
    }
}
