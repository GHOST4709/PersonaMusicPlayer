package com.example.personabgm.context

import com.example.personabgm.domain.model.LatLng

data class ProximityZone(val id: String, val lat: Double, val lng: Double, val radiusM: Float)

/** One reading from the device's location sensor. `atMs` is epoch millis (the same clock the zone state uses). */
data class LocationFix(val lat: Double, val lng: Double, val accuracyM: Float, val atMs: Long)

sealed interface ProximityEvent {
    val zoneId: String
    val atMs: Long

    /** Inside long enough to count as arriving (the local counterpart of a geofence DWELL). */
    data class Dwell(override val zoneId: String, override val atMs: Long) : ProximityEvent

    /** Outside long enough to count as leaving (the local counterpart of a geofence EXIT). */
    data class Exit(override val zoneId: String, override val atMs: Long) : ProximityEvent
}

/**
 * Optional fast path for spec 5.2: while the app is open, decide arrival and departure from the device's own
 * location readings instead of waiting a minute or more for Google Play services to report a geofence event.
 *
 * Deliberately conservative, because a wrong guess flips the music:
 *  - A fix only counts as "inside" if the whole accuracy circle is inside the zone, and only counts as
 *    "outside" if the whole accuracy circle is outside it.
 *  - Arriving needs [dwellMs] of continuous "inside" (the same 15 s as the real geofence); leaving needs
 *    [exitConfirmMs] of continuous "outside".
 *  - Fixes worse than [maxAccuracyM] are ignored completely.
 *  - It only reports what the stored state does not already say: "inside" zones are never re-dwelled, so
 *    overlapping zones keep their order, and it never reports an exit for a zone not stored as inside.
 *
 * Pure and clock-free: every reading carries its own time.
 */
class ProximityTracker(
    private val dwellMs: Long = DWELL_MS,
    private val exitConfirmMs: Long = EXIT_CONFIRM_MS,
    private val maxAccuracyM: Float = MAX_ACCURACY_M,
) {
    private val enterSince = HashMap<String, Long>()
    private val exitSince = HashMap<String, Long>()

    /**
     * @param insideNow zone ids the stored geofence state currently says the person is inside.
     * @return the transitions this reading completes (usually none).
     */
    fun onFix(fix: LocationFix, zones: List<ProximityZone>, insideNow: Set<String>): List<ProximityEvent> {
        val present = zones.mapTo(HashSet()) { it.id }
        enterSince.keys.retainAll(present)
        exitSince.keys.retainAll(present)

        if (fix.accuracyM < 0f || fix.accuracyM > maxAccuracyM) return emptyList()

        val events = ArrayList<ProximityEvent>()
        for (zone in zones) {
            val distanceM = WeatherPolicy.distanceKm(LatLng(fix.lat, fix.lng), LatLng(zone.lat, zone.lng)) * 1000.0
            val confidentlyInside = distanceM + fix.accuracyM <= zone.radiusM
            val confidentlyOutside = distanceM - fix.accuracyM > zone.radiusM

            if (zone.id !in insideNow) {
                exitSince.remove(zone.id)
                if (confidentlyInside) {
                    val since = enterSince.getOrPut(zone.id) { fix.atMs }
                    if (fix.atMs - since >= dwellMs) {
                        events += ProximityEvent.Dwell(zone.id, fix.atMs)
                        enterSince.remove(zone.id)
                    }
                } else {
                    enterSince.remove(zone.id)
                }
            } else {
                enterSince.remove(zone.id)
                if (confidentlyOutside) {
                    val since = exitSince.getOrPut(zone.id) { fix.atMs }
                    if (fix.atMs - since >= exitConfirmMs) {
                        events += ProximityEvent.Exit(zone.id, fix.atMs)
                        exitSince.remove(zone.id)
                    }
                } else {
                    exitSince.remove(zone.id)
                }
            }
        }
        return events
    }

    fun reset() {
        enterSince.clear()
        exitSince.clear()
    }

    companion object {
        /** Same dwell as the real geofence (spec 5.1). */
        const val DWELL_MS = 15_000L
        const val EXIT_CONFIRM_MS = 5_000L

        /** Readings vaguer than this say too little about which side of a 100 m boundary you are on. */
        const val MAX_ACCURACY_M = 100f
    }
}
