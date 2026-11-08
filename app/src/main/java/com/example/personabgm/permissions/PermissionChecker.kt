package com.example.personabgm.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.personabgm.domain.permissions.ApiLevel
import com.example.personabgm.domain.permissions.PermissionSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads what the app is allowed to do right now. The decisions about it are in the pure `domain.permissions`. */
@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun snapshot(): PermissionSnapshot {
        val sdk = Build.VERSION.SDK_INT
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return PermissionSnapshot(
            fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            coarseLocation = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            // Before Android 10 foreground location already covers the background.
            backgroundLocation = sdk < ApiLevel.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            // Also false when the person switched notifications off for the app in system settings.
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            bluetoothConnect = sdk < ApiLevel.S || granted(Manifest.permission.BLUETOOTH_CONNECT),
            locationServicesOn = LocationManagerCompat.isLocationEnabled(locationManager),
        )
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
