package com.example.personabgm.domain.permissions

import com.example.personabgm.domain.permissions.OnboardingStep.BACKGROUND_LOCATION
import com.example.personabgm.domain.permissions.OnboardingStep.BATTERY
import com.example.personabgm.domain.permissions.OnboardingStep.BLUETOOTH
import com.example.personabgm.domain.permissions.OnboardingStep.LOCATION
import com.example.personabgm.domain.permissions.OnboardingStep.NOTIFICATIONS
import com.example.personabgm.domain.permissions.OnboardingStep.WELCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPlanTest {

    private val none = PermissionSnapshot(
        fineLocation = false, coarseLocation = false, backgroundLocation = false, notifications = false,
        batteryExempt = false, bluetoothConnect = false, locationServicesOn = true,
    )
    private val all = none.copy(
        fineLocation = true, coarseLocation = true, backgroundLocation = true, notifications = true,
        batteryExempt = true, bluetoothConnect = true,
    )

    @Test fun androidEightHasNoBackgroundNotificationOrBluetoothStep() =
        assertEquals(listOf(WELCOME, LOCATION, BATTERY), OnboardingPlan.steps(26))

    @Test fun androidTenAddsBackgroundLocation() =
        assertEquals(listOf(WELCOME, LOCATION, BACKGROUND_LOCATION, BATTERY), OnboardingPlan.steps(29))

    @Test fun androidTwelveAddsBluetoothLast() =
        assertEquals(listOf(WELCOME, LOCATION, BACKGROUND_LOCATION, BATTERY, BLUETOOTH), OnboardingPlan.steps(31))

    @Test fun androidThirteenAndNewerHaveEverythingInTheSpecOrder() {
        val expected = listOf(WELCOME, LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, BATTERY, BLUETOOTH)
        assertEquals(expected, OnboardingPlan.steps(33))
        assertEquals(expected, OnboardingPlan.steps(36))
    }

    @Test fun backgroundLocationAlwaysComesAfterForegroundLocation() {
        for (sdk in 26..37) {
            val steps = OnboardingPlan.steps(sdk)
            if (BACKGROUND_LOCATION in steps) assertTrue(steps.indexOf(BACKGROUND_LOCATION) > steps.indexOf(LOCATION))
        }
    }

    @Test fun satisfiedFollowsTheMatchingPermission() {
        assertTrue(OnboardingPlan.isSatisfied(WELCOME, none))
        assertFalse(OnboardingPlan.isSatisfied(LOCATION, none))
        assertTrue(OnboardingPlan.isSatisfied(LOCATION, none.copy(fineLocation = true)))
        assertTrue(OnboardingPlan.isSatisfied(BACKGROUND_LOCATION, none.copy(backgroundLocation = true)))
        assertTrue(OnboardingPlan.isSatisfied(NOTIFICATIONS, none.copy(notifications = true)))
        assertTrue(OnboardingPlan.isSatisfied(BATTERY, none.copy(batteryExempt = true)))
        assertTrue(OnboardingPlan.isSatisfied(BLUETOOTH, none.copy(bluetoothConnect = true)))
        OnboardingStep.entries.forEach { assertTrue(OnboardingPlan.isSatisfied(it, all)) }
    }

    @Test fun approximateLocationDoesNotSatisfyTheLocationStep() {
        val approximate = none.copy(coarseLocation = true)
        assertFalse(OnboardingPlan.isSatisfied(LOCATION, approximate))
        assertTrue(approximate.approximateOnly)
        assertFalse(all.approximateOnly)
    }

    @Test fun backgroundLocationCannotBeRequestedBeforeForegroundLocation() {
        assertFalse(OnboardingPlan.isReady(BACKGROUND_LOCATION, none))
        assertFalse(OnboardingPlan.isReady(BACKGROUND_LOCATION, none.copy(coarseLocation = true)))
        assertTrue(OnboardingPlan.isReady(BACKGROUND_LOCATION, none.copy(fineLocation = true)))
        listOf(WELCOME, LOCATION, NOTIFICATIONS, BATTERY, BLUETOOTH).forEach { assertTrue(OnboardingPlan.isReady(it, none)) }
    }

    @Test fun onlyBluetoothIsOptional() {
        OnboardingStep.entries.forEach { assertEquals(it == BLUETOOTH, OnboardingPlan.isOptional(it)) }
    }
}
