package com.example.personabgm.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityTrackerTest {

    // About 0.0009 degrees of latitude is 100 m.
    private val zone = ProximityZone("z", lat = 48.8566, lng = 2.3522, radiusM = 150f)
    private fun at(metresNorth: Double, t: Long, accuracy: Float = 10f) =
        LocationFix(zone.lat + metresNorth / 111_195.0, zone.lng, accuracy, t)

    private fun tracker() = ProximityTracker(dwellMs = 15_000, exitConfirmMs = 5_000, maxAccuracyM = 100f)
    private val none = emptySet<String>()
    private val inside = setOf("z")

    private fun ProximityTracker.feed(fix: LocationFix, insideNow: Set<String> = none) =
        onFix(fix, listOf(zone), insideNow)

    // ---- arriving ----

    @Test fun staysQuietBeforeTheDwellTimeHasPassed() {
        val t = tracker()
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(0.0, 0)))
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(0.0, 14_999)))
    }

    @Test fun reportsADwellOnceTheDwellTimePasses() {
        val t = tracker()
        t.feed(at(0.0, 0))
        assertEquals(listOf<ProximityEvent>(ProximityEvent.Dwell("z", 15_000)), t.feed(at(0.0, 15_000)))
    }

    @Test fun leavingBeforeTheDwellCompletesResetsTheTimer() {
        val t = tracker()
        t.feed(at(0.0, 0))
        t.feed(at(400.0, 8_000))                           // clearly outside: candidate dropped
        t.feed(at(0.0, 9_000))                             // back inside: a fresh 15 s starts here
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(0.0, 20_000)))
        assertEquals(1, t.feed(at(0.0, 24_000)).size)
    }

    @Test fun reportsOnlyOnceEvenIfTheStoredStateIsSlowToUpdate() {
        val t = tracker()
        t.feed(at(0.0, 0))
        assertEquals(1, t.feed(at(0.0, 15_000)).size)
        // The stored state still says "outside" for a moment: it has to wait a full new dwell.
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(0.0, 16_000)))
    }

    @Test fun aZoneAlreadyStoredAsInsideIsNeverDwelledAgain() {
        val t = tracker()
        t.feed(at(0.0, 0), inside)
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(0.0, 60_000), inside))
    }

    // ---- accuracy ----

    @Test fun aFixThatMightBeOutsideDoesNotCountAsInside() {
        val t = tracker()
        // 120 m from the centre with 40 m accuracy: the accuracy circle reaches 160 m, beyond the 150 m radius.
        t.feed(at(120.0, 0, accuracy = 40f))
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(120.0, 30_000, accuracy = 40f)))
    }

    @Test fun aFixWhoseWholeAccuracyCircleIsInsideCounts() {
        val t = tracker()
        t.feed(at(100.0, 0, accuracy = 40f))               // 100 + 40 = 140 <= 150
        assertEquals(1, t.feed(at(100.0, 15_000, accuracy = 40f)).size)
    }

    @Test fun fixesWorseThanTheLimitAreIgnoredAndDoNotResetTheTimer() {
        val t = tracker()
        t.feed(at(0.0, 0))
        t.feed(at(0.0, 5_000, accuracy = 500f))            // useless fix: ignored
        assertEquals(1, t.feed(at(0.0, 15_000)).size)
    }

    @Test fun negativeAccuracyIsTreatedAsInvalid() {
        val t = tracker()
        t.feed(at(0.0, 0, accuracy = -1f))
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(0.0, 60_000, accuracy = -1f)))
    }

    // ---- leaving ----

    @Test fun reportsAnExitAfterTheConfirmTime() {
        val t = tracker()
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(400.0, 0), inside))
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(400.0, 4_999), inside))
        assertEquals(listOf<ProximityEvent>(ProximityEvent.Exit("z", 5_000)), t.feed(at(400.0, 5_000), inside))
    }

    @Test fun comingBackInsideCancelsAPendingExit() {
        val t = tracker()
        t.feed(at(400.0, 0), inside)
        t.feed(at(0.0, 3_000), inside)                    // back in the zone
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(400.0, 6_000), inside)) // the 5 s starts over here
        assertEquals(1, t.feed(at(400.0, 11_000), inside).size)
    }

    @Test fun theBlurryEdgeNeitherEntersNorExits() {
        val t = tracker()
        // 160 m out with 30 m accuracy: could still be inside, so it is not a confident exit.
        t.feed(at(160.0, 0, accuracy = 30f), inside)
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(160.0, 60_000, accuracy = 30f), inside))
    }

    @Test fun neverReportsAnExitForAZoneNotStoredAsInside() {
        val t = tracker()
        t.feed(at(400.0, 0), none)
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(400.0, 60_000), none))
    }

    // ---- several zones, bookkeeping ----

    @Test fun zonesAreTrackedIndependently() {
        val near = ProximityZone("near", zone.lat, zone.lng, 150f)
        val far = ProximityZone("far", zone.lat + 0.05, zone.lng, 150f)
        val t = tracker()
        val zones = listOf(near, far)
        t.onFix(LocationFix(zone.lat, zone.lng, 10f, 0), zones, none)
        val events = t.onFix(LocationFix(zone.lat, zone.lng, 10f, 15_000), zones, none)
        assertEquals(listOf<ProximityEvent>(ProximityEvent.Dwell("near", 15_000)), events)
    }

    @Test fun aDeletedZoneForgetsItsTimers() {
        val t = tracker()
        t.feed(at(0.0, 0))
        t.onFix(at(0.0, 10_000), emptyList(), none)        // zone removed
        // Re-added: the old half-finished dwell must not count.
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(0.0, 16_000)))
    }

    @Test fun resetForgetsEverything() {
        val t = tracker()
        t.feed(at(0.0, 0))
        t.reset()
        assertEquals(emptyList<ProximityEvent>(), t.feed(at(0.0, 15_000)))
        assertTrue(t.feed(at(0.0, 30_000)).isNotEmpty())
    }

    @Test fun defaultsMatchTheSpecDwell() {
        assertEquals(15_000L, ProximityTracker.DWELL_MS)
    }
}
