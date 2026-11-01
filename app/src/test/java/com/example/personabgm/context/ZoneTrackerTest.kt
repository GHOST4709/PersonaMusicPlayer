package com.example.personabgm.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneTrackerTest {

    private val grace = 15_000L
    private fun tracker() = ZoneTracker(grace)
    private fun inside(id: String, at: Long) = ZoneSignal(id, true, at)
    private fun outside(id: String, at: Long) = ZoneSignal(id, false, at)

    @Test fun nothingIsActiveInitially() {
        assertNull(tracker().activeZoneId())
        assertNull(tracker().nextDeadlineMs())
    }

    @Test fun dwellActivatesTheZone() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000)), nowMs = 1_000)
        assertEquals("a", t.activeZoneId())
    }

    @Test fun exitIsAppliedOnlyAfterTheGraceWindow() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000)), 1_000)
        t.update(listOf(outside("a", 10_000)), 10_000)
        assertEquals("a", t.activeZoneId())                 // still in grace
        assertEquals(25_000L, t.nextDeadlineMs())
        t.advance(24_999); assertEquals("a", t.activeZoneId())
        t.advance(25_000); assertNull(t.activeZoneId())     // grace elapsed
        assertNull(t.nextDeadlineMs())
    }

    @Test fun enteringAgainWithinGraceCancelsTheExit() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000)), 1_000)
        t.update(listOf(outside("a", 10_000)), 10_000)
        t.update(listOf(inside("a", 14_000)), 14_000)
        assertNull(t.nextDeadlineMs())
        t.advance(60_000)
        assertEquals("a", t.activeZoneId())
    }

    @Test fun mostRecentDwellWins() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000), inside("b", 5_000)), 5_000)
        assertEquals("b", t.activeZoneId())
    }

    @Test fun whenTheNewestZoneExitsTheOlderOneTakesOver() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000), inside("b", 5_000)), 5_000)
        t.update(listOf(inside("a", 1_000), outside("b", 9_000)), 9_000)
        assertEquals("b", t.activeZoneId())     // grace
        t.advance(24_000)
        assertEquals("a", t.activeZoneId())     // fall back, not roaming
    }

    @Test fun exitOfAnOlderZoneDoesNotChangeTheActiveOne() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000), inside("b", 5_000)), 5_000)
        t.update(listOf(outside("a", 6_000), inside("b", 5_000)), 6_000)
        t.advance(21_000)
        assertEquals("b", t.activeZoneId())
    }

    @Test fun aNewerDwellOfTheSameZoneMovesItToTheFront() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000), inside("b", 5_000)), 5_000)
        t.update(listOf(inside("a", 8_000), inside("b", 5_000)), 8_000) // geofences re-registered, "a" dwelled again
        assertEquals("a", t.activeZoneId())
    }

    @Test fun anOlderDuplicateSignalDoesNotMoveTheZoneBack() {
        val t = tracker()
        t.update(listOf(inside("a", 8_000)), 8_000)
        t.update(listOf(inside("a", 2_000)), 9_000)
        t.update(listOf(inside("a", 8_000), inside("b", 5_000)), 9_000)
        assertEquals("a", t.activeZoneId())
    }

    @Test fun aDeletedOrDisabledZoneDisappearsImmediatelyWithoutGrace() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000)), 1_000)
        t.update(emptyList(), 2_000)
        assertNull(t.activeZoneId())
        assertNull(t.nextDeadlineMs())
    }

    @Test fun aZoneOnlyEverSeenOutsideIsNeverActive() {
        val t = tracker()
        t.update(listOf(outside("a", 1_000)), 1_000)
        assertNull(t.activeZoneId())
        assertNull(t.nextDeadlineMs())
    }

    @Test fun anExitThatIsAlreadyOldWhenObservedAppliesAtOnce() {
        // For example the service restarted a minute after the person left.
        val t = tracker()
        t.update(listOf(inside("a", 1_000)), 1_000)
        t.update(listOf(outside("a", 2_000)), 60_000)
        assertNull(t.activeZoneId())
    }

    @Test fun tiesOnDwellTimeAreBrokenDeterministicallyByLowestId() {
        val t = tracker()
        t.update(listOf(inside("b", 5_000), inside("a", 5_000)), 5_000)
        assertEquals("a", t.activeZoneId())
    }

    @Test fun nextDeadlineIsTheEarliestPendingExit() {
        val t = tracker()
        t.update(listOf(inside("a", 1_000), inside("b", 2_000)), 2_000)
        t.update(listOf(outside("a", 10_000), outside("b", 4_000)), 10_000)
        assertEquals(19_000L, t.nextDeadlineMs())  // b: 4_000 + 15_000
    }
}
