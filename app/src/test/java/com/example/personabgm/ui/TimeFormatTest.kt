package com.example.personabgm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun formatsEachRange() {
        assertEquals("just now", formatAge(0))
        assertEquals("just now", formatAge(4_999))
        assertEquals("5 s ago", formatAge(5_000))
        assertEquals("59 s ago", formatAge(59_999))
        assertEquals("1 min ago", formatAge(60_000))
        assertEquals("59 min ago", formatAge(3_599_999))
        assertEquals("1 h ago", formatAge(3_600_000))
        assertEquals("23 h ago", formatAge(86_399_999))
        assertEquals("1 d ago", formatAge(86_400_000))
        assertEquals("30 d ago", formatAge(30L * 86_400_000))
    }

    @Test fun aNegativeAgeIsFlagged() = assertEquals("in the future", formatAge(-1))
}
