package com.example.personabgm.domain.model

/** Validation and naming rules for zones (spec 5.1). Pure, so the repository and UI share one source of truth. */
object ZoneRules {
    const val DEFAULT_RADIUS_M = 150f
    const val MIN_RADIUS_M = 100f
    const val MAX_RADIUS_M = 500f

    /** Android allows 100 geofences per app per device user. */
    const val MAX_ZONES = 100

    fun clampRadius(radiusM: Float): Float = radiusM.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)

    fun canAddAnother(currentCount: Int): Boolean = currentCount < MAX_ZONES

    /** "Zone 1", "Zone 2", ...: the smallest number not already used by an existing name. */
    fun nextDefaultName(existingNames: Collection<String>): String {
        val taken = existingNames.toHashSet()
        var n = 1
        while ("Zone $n" in taken) n++
        return "Zone $n"
    }

    fun isValidLocation(lat: Double, lng: Double): Boolean =
        lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)
}
