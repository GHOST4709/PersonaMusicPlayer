package com.example.personabgm.ui.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.personabgm.domain.permissions.ApiLevel

/**
 * Every "ask for a permission" and "open a system screen" the app needs, in one place, so onboarding, the
 * Permission Health card, Settings, and Zones all behave identically.
 *
 * Runtime permissions go through one launcher whose result calls back so the caller can re-read the state.
 * Settings screens that can be missing on some devices fall back to the app's own details page.
 */
@Stable
class PermissionActions internal constructor(
    private val context: Context,
    private val requestRuntime: (Array<String>) -> Unit,
) {
    fun requestLocation() =
        requestRuntime(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    /**
     * Android 10 shows a dialog with "Allow all the time". From Android 11 the same call sends the person to
     * the app's location settings page instead, because the system no longer shows a dialog for it.
     */
    fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= ApiLevel.Q) requestRuntime(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= ApiLevel.TIRAMISU) {
            requestRuntime(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            openNotificationSettings()
        }
    }

    fun requestBluetooth() {
        if (Build.VERSION.SDK_INT >= ApiLevel.S) requestRuntime(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
    }

    /** Asks to be excluded from battery optimization (spec 5.7). Falls back to the settings list. */
    fun requestBatteryExemption() = start(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun openBatterySettings() = start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), fallback = appSettings())

    fun openAppSettings() = start(appSettings())

    fun openLocationSettings() = start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun openNotificationSettings() = start(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        fallback = appSettings(),
    )

    /** "Pause app activity if unused" (Android 11+): left on, the system eventually removes granted permissions. */
    fun openAutoRevokeSettings() {
        if (Build.VERSION.SDK_INT >= ApiLevel.R) {
            start(
                Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.fromParts("package", context.packageName, null)),
                fallback = appSettings(),
            )
        }
    }

    fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun appSettings() =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    private fun start(intent: Intent, fallback: Intent? = null) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallback == null) {
                Log.w(TAG, "No activity for ${intent.action}", e)
                return
            }
            try {
                context.startActivity(fallback)
            } catch (e2: ActivityNotFoundException) {
                Log.w(TAG, "No activity for fallback ${fallback.action}", e2)
            }
        }
    }

    private companion object {
        const val TAG = "PermissionActions"
    }
}

/** [onResult] runs after any runtime permission dialog closes (granted or not). */
@Composable
fun rememberPermissionActions(onResult: () -> Unit): PermissionActions {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentOnResult()
    }
    return remember(context, launcher) { PermissionActions(context) { permissions -> launcher.launch(permissions) } }
}
