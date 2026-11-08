package com.example.personabgm.ui

/** "5 s ago", "12 min ago" ... for the debug panel. Negative ages (clock went backwards) are called out. */
fun formatAge(ageMs: Long): String = when {
    ageMs < 0L -> "in the future"
    ageMs < 5_000L -> "just now"
    ageMs < 60_000L -> "${ageMs / 1_000L} s ago"
    ageMs < 3_600_000L -> "${ageMs / 60_000L} min ago"
    ageMs < 86_400_000L -> "${ageMs / 3_600_000L} h ago"
    else -> "${ageMs / 86_400_000L} d ago"
}
