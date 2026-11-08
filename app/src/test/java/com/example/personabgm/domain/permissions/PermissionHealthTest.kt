package com.example.personabgm.domain.permissions

import com.example.personabgm.domain.permissions.HealthIssue.BACKGROUND_LOCATION_MISSING
import com.example.personabgm.domain.permissions.HealthIssue.BATTERY_RESTRICTED
import com.example.personabgm.domain.permissions.HealthIssue.LOCATION_APPROXIMATE_ONLY
import com.example.personabgm.domain.permissions.HealthIssue.LOCATION_MISSING
import com.example.personabgm.domain.permissions.HealthIssue.NOTIFICATIONS_OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHealthTest {

    private val healthy = PermissionSnapshot(
        fineLocation = true, coarseLocation = true, backgroundLocation = true, notifications = true,
        batteryExempt = true, bluetoothConnect = false, locationServicesOn = true,
    )

    private fun issues(s: PermissionSnapshot, sdk: Int = 34) = PermissionHealth.issues(s, sdk)

    @Test fun aFullyPermittedAppHasNoIssuesEvenWithoutBluetooth() = assertEquals(emptyList<HealthIssue>(), issues(healthy))

    @Test fun missingLocationIsReported() =
        assertEquals(listOf(LOCATION_MISSING), issues(healthy.copy(fineLocation = false, coarseLocation = false, backgroundLocation = false)))

    @Test fun approximateOnlyIsADistinctIssue() =
        assertEquals(listOf(LOCATION_APPROXIMATE_ONLY), issues(healthy.copy(fineLocation = false, backgroundLocation = false)))

    @Test fun missingBackgroundLocationIsReportedOnlyWhenForegroundIsGranted() {
        assertEquals(listOf(BACKGROUND_LOCATION_MISSING), issues(healthy.copy(backgroundLocation = false)))
        // With no foreground location the first thing to fix is that, not the background step.
        assertEquals(
            listOf(LOCATION_MISSING),
            issues(healthy.copy(fineLocation = false, coarseLocation = false, backgroundLocation = false)),
        )
    }

    @Test fun backgroundLocationIsNeverReportedBeforeAndroidTen() =
        assertEquals(emptyList<HealthIssue>(), issues(healthy.copy(backgroundLocation = false), sdk = 28))

    @Test fun issuesAreOrderedMostImportantFirst() {
        val worst = healthy.copy(backgroundLocation = false, notifications = false, batteryExempt = false)
        assertEquals(listOf(BACKGROUND_LOCATION_MISSING, NOTIFICATIONS_OFF, BATTERY_RESTRICTED), issues(worst))
    }

    @Test fun bluetoothIsNeverAHealthIssue() =
        assertEquals(emptyList<HealthIssue>(), issues(healthy.copy(bluetoothConnect = false)))

    @Test fun locationIssuesAreCriticalAndTheRestAreNot() {
        assertTrue(PermissionHealth.isCritical(LOCATION_MISSING))
        assertTrue(PermissionHealth.isCritical(LOCATION_APPROXIMATE_ONLY))
        assertTrue(PermissionHealth.isCritical(BACKGROUND_LOCATION_MISSING))
        assertFalse(PermissionHealth.isCritical(NOTIFICATIONS_OFF))
        assertFalse(PermissionHealth.isCritical(BATTERY_RESTRICTED))
    }

    @Test fun gpsBannerShowsOnlyAfterSetupAndOnlyWhenLocationServicesAreOff() {
        val off = healthy.copy(locationServicesOn = false)
        assertTrue(PermissionHealth.showGpsBanner(off, onboardingCompleted = true))
        assertFalse(PermissionHealth.showGpsBanner(off, onboardingCompleted = false))
        assertFalse(PermissionHealth.showGpsBanner(healthy, onboardingCompleted = true))
    }
}
