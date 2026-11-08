package com.example.personabgm.domain.debug

data class DebugSection(val title: String, val lines: List<String>)

/** Turns the debug panel's sections into one block of plain text that can be pasted into a bug report. */
object DebugReport {
    fun format(sections: List<DebugSection>): String =
        sections
            .filter { it.lines.isNotEmpty() }
            .joinToString(separator = "\n\n") { section ->
                "== ${section.title} ==\n" + section.lines.joinToString(separator = "\n")
            }
}
