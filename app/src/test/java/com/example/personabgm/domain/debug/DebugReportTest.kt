package com.example.personabgm.domain.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugReportTest {
    @Test fun formatsSectionsInOrderSeparatedByABlankLine() {
        val text = DebugReport.format(
            listOf(
                DebugSection("Build", listOf("version 0.6.0", "debug")),
                DebugSection("Device", listOf("Pixel 8, API 35")),
            ),
        )
        assertEquals("== Build ==\nversion 0.6.0\ndebug\n\n== Device ==\nPixel 8, API 35", text)
    }

    @Test fun skipsEmptySections() {
        val text = DebugReport.format(listOf(DebugSection("Empty", emptyList()), DebugSection("Zones", listOf("none"))))
        assertEquals("== Zones ==\nnone", text)
    }

    @Test fun noSectionsGivesAnEmptyReport() = assertEquals("", DebugReport.format(emptyList()))
}
