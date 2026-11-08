package com.example.personabgm.domain.permissions

enum class OnboardingStep { WELCOME, LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, BATTERY, BLUETOOTH }

/**
 * The setup flow of spec 3.2, adapted to the Android version it runs on.
 *
 * Order: Welcome, Location, Background location, Notifications, Battery, Bluetooth. Background location
 * is always its own step after foreground location (the platform will not grant it together), and
 * Bluetooth comes last because it is optional.
 */
object OnboardingPlan {

    fun steps(sdk: Int): List<OnboardingStep> = buildList {
        add(OnboardingStep.WELCOME)
        add(OnboardingStep.LOCATION)
        if (sdk >= ApiLevel.Q) add(OnboardingStep.BACKGROUND_LOCATION)
        if (sdk >= ApiLevel.TIRAMISU) add(OnboardingStep.NOTIFICATIONS)
        add(OnboardingStep.BATTERY)
        if (sdk >= ApiLevel.S) add(OnboardingStep.BLUETOOTH)
    }

    fun isSatisfied(step: OnboardingStep, snapshot: PermissionSnapshot): Boolean = when (step) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.LOCATION -> snapshot.fineLocation
        OnboardingStep.BACKGROUND_LOCATION -> snapshot.backgroundLocation
        OnboardingStep.NOTIFICATIONS -> snapshot.notifications
        OnboardingStep.BATTERY -> snapshot.batteryExempt
        OnboardingStep.BLUETOOTH -> snapshot.bluetoothConnect
    }

    /**
     * Whether the step's action can be taken right now. Background location cannot be requested until
     * foreground location has been granted.
     */
    fun isReady(step: OnboardingStep, snapshot: PermissionSnapshot): Boolean = when (step) {
        OnboardingStep.BACKGROUND_LOCATION -> snapshot.fineLocation
        else -> true
    }

    /** Optional steps are labelled as such; every step can still be skipped, with a reminder later. */
    fun isOptional(step: OnboardingStep): Boolean = step == OnboardingStep.BLUETOOTH
}
