package com.example.personabgm.domain.permissions

enum class HealthIssue {
    LOCATION_MISSING,
    LOCATION_APPROXIMATE_ONLY,
    BACKGROUND_LOCATION_MISSING,
    NOTIFICATIONS_OFF,
    BATTERY_RESTRICTED,
}

/**
 * Re-checked on every resume (spec 3.2 "Permission Health card"): anything revoked or never granted shows
 * up with a one-tap fix. Ordered most important first. Bluetooth is optional and never reported.
 */
object PermissionHealth {

    fun issues(snapshot: PermissionSnapshot, sdk: Int): List<HealthIssue> = buildList {
        when {
            snapshot.approximateOnly -> add(HealthIssue.LOCATION_APPROXIMATE_ONLY)
            !snapshot.fineLocation -> add(HealthIssue.LOCATION_MISSING)
            sdk >= ApiLevel.Q && !snapshot.backgroundLocation -> add(HealthIssue.BACKGROUND_LOCATION_MISSING)
        }
        if (!snapshot.notifications) add(HealthIssue.NOTIFICATIONS_OFF)
        if (!snapshot.batteryExempt) add(HealthIssue.BATTERY_RESTRICTED)
    }

    /** Critical issues stop zones from working at all, so they cannot be dismissed for the session. */
    fun isCritical(issue: HealthIssue): Boolean = when (issue) {
        HealthIssue.LOCATION_MISSING,
        HealthIssue.LOCATION_APPROXIMATE_ONLY,
        HealthIssue.BACKGROUND_LOCATION_MISSING,
        -> true

        HealthIssue.NOTIFICATIONS_OFF,
        HealthIssue.BATTERY_RESTRICTED,
        -> false
    }

    /** The high-contrast "turn on Location Services" banner (spec 3.2). Not shown during first-run setup. */
    fun showGpsBanner(snapshot: PermissionSnapshot, onboardingCompleted: Boolean): Boolean =
        onboardingCompleted && !snapshot.locationServicesOn
}
