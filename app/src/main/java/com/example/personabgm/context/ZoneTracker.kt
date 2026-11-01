package com.example.personabgm.context

/** Latest persisted geofence state of one zone (Room `zone_state`). */
data class ZoneSignal(
    val zoneId: String,
    val inside: Boolean,
    /** Epoch millis of the DWELL (inside) or EXIT (outside) that produced this state. */
    val sinceMs: Long,
)

/**
 * Turns persisted geofence states into "which zone is active right now", with the rules of spec 5.3 and 5.6:
 *
 *  - The DWELL's 15 s delay is already done by Play services; this class only adds the exit side.
 *  - An EXIT is applied after a [graceMs] window, cancelled if the zone is entered again. This stops a
 *    person walking along a boundary from flapping the music.
 *  - With overlapping zones, the most recent DWELL wins. When it exits, any other zone still
 *    inside takes over; with none, the result is null (roaming).
 *  - A zone that no longer exists (deleted or disabled) disappears at once, without a grace window.
 *
 * Pure and clock-free: callers pass `nowMs`, so every rule is unit-testable.
 */
class ZoneTracker(private val graceMs: Long = DEFAULT_GRACE_MS) {

    private val insideSince = LinkedHashMap<String, Long>()
    private val exitDeadline = HashMap<String, Long>()

    fun update(signals: List<ZoneSignal>, nowMs: Long) {
        val present = signals.mapTo(HashSet()) { it.zoneId }
        insideSince.keys.retainAll(present)
        exitDeadline.keys.retainAll(present)

        for (signal in signals) {
            if (signal.inside) {
                val known = insideSince[signal.zoneId]
                if (known == null || signal.sinceMs > known) insideSince[signal.zoneId] = signal.sinceMs
                exitDeadline.remove(signal.zoneId) // entered again: cancel any pending exit
            } else if (signal.zoneId in insideSince && signal.zoneId !in exitDeadline) {
                exitDeadline[signal.zoneId] = signal.sinceMs + graceMs
            }
        }
        advance(nowMs)
    }

    /** Applies every exit whose grace window has elapsed. */
    fun advance(nowMs: Long) {
        val due = exitDeadline.filterValues { it <= nowMs }.keys.toList()
        for (zoneId in due) {
            exitDeadline.remove(zoneId)
            insideSince.remove(zoneId)
        }
    }

    /** When the next pending exit becomes due, or null if none is pending. */
    fun nextDeadlineMs(): Long? = exitDeadline.values.minOrNull()

    /** The zone that should currently drive the music, or null when roaming. */
    fun activeZoneId(): String? =
        insideSince.entries
            .maxWithOrNull(compareBy<Map.Entry<String, Long>>({ it.value }).thenByDescending { it.key })
            ?.key

    companion object {
        /** Spec 5.6: 15 s grace before applying an EXIT. */
        const val DEFAULT_GRACE_MS = 15_000L
    }
}
