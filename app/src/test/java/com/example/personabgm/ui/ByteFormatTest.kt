package com.example.personabgm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {
    @Test fun formatsEachMagnitude() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("5.0 MB", formatBytes(5L * 1024 * 1024))
        assertEquals("500.0 MB", formatBytes(500L * 1024 * 1024))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }
}
