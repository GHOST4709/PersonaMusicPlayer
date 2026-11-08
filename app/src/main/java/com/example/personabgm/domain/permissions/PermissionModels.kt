package com.example.personabgm.domain.permissions

/** Android API levels that change which permissions exist (named so call sites read clearly). */
object ApiLevel {
    const val Q = 29          // Android 10: ACCESS_BACKGROUND_LOCATION exists
    const val R = 30          // Android 11: background location is granted in Settings, not by a dialog
    const val S = 31          // Android 12: BLUETOOTH_CONNECT is a runtime permission
    const val TIRAMISU = 33   // Android 13: POST_NOTIFICATIONS is a runtime permission
}

/** What the app is currently allowed to do. Built on the device by `PermissionChecker`; pure from here on. */
data class PermissionSnapshot(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    /** Always true before Android 10, where foreground location already covers the background. */
    val backgroundLocation: Boolean,
    /** Notifications are shown at all (covers the Android 13 permission and the per-app switch). */
    val notifications: Boolean,
    val batteryExempt: Boolean,
    /** Always true before Android 12. */
    val bluetoothConnect: Boolean,
    /** The device's Location Services master switch. */
    val locationServicesOn: Boolean,
) {
    /** The person allowed only "approximate" location, which is too coarse for zone detection. */
    val approximateOnly: Boolean get() = coarseLocation && !fineLocation
}
