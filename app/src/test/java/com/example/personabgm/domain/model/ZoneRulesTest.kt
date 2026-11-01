package com.example.personabgm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneRulesTest {
    @Test fun radiusIsClampedToTheSupportedRange() {
        assertEquals(100f, ZoneRules.clampRadius(75f))        // the v1 default is below the platform minimum
        assertEquals(100f, ZoneRules.clampRadius(-5f))
        assertEquals(150f, ZoneRules.clampRadius(150f))
        assertEquals(500f, ZoneRules.clampRadius(900f))
    }

    @Test fun defaultsMatchTheSpec() {
        assertEquals(150f, ZoneRules.DEFAULT_RADIUS_M)
        assertEquals(100, ZoneRules.MAX_ZONES)
    }

    @Test fun theHundredAndFirstZoneIsRejected() {
        assertTrue(ZoneRules.canAddAnother(0))
        assertTrue(ZoneRules.canAddAnother(99))
        assertFalse(ZoneRules.canAddAnother(100))
        assertFalse(ZoneRules.canAddAnother(250))
    }

    @Test fun defaultNameUsesTheSmallestFreeNumber() {
        assertEquals("Zone 1", ZoneRules.nextDefaultName(emptyList()))
        assertEquals("Zone 3", ZoneRules.nextDefaultName(listOf("Zone 1", "Zone 2")))
        assertEquals("Zone 2", ZoneRules.nextDefaultName(listOf("Zone 1", "Zone 3")))
        assertEquals("Zone 1", ZoneRules.nextDefaultName(listOf("Home", "Work")))
    }

    @Test fun locationValidation() {
        assertTrue(ZoneRules.isValidLocation(48.85, 2.35))
        assertTrue(ZoneRules.isValidLocation(-90.0, 180.0))
        assertFalse(ZoneRules.isValidLocation(91.0, 0.0))
        assertFalse(ZoneRules.isValidLocation(0.0, -181.0))
        assertFalse(ZoneRules.isValidLocation(0.0, 0.0))      // "null island" almost always means an unset location
        assertFalse(ZoneRules.isValidLocation(Double.NaN, 1.0))
    }
}
