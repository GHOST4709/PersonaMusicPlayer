package com.example.personabgm.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.personabgm.domain.model.LatLng
import com.example.personabgm.util.awaitResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Device location and the permission checks around it. */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasFinePermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Needed for geofence events while the app is closed. Always true before Android 10. */
    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun hasAnyLocationPermission(): Boolean =
        hasFinePermission() || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** The most recent cached fix. Cheap, may be old or null. Used for weather. */
    @SuppressLint("MissingPermission") // guarded by hasAnyLocationPermission()
    suspend fun lastKnown(): LatLng? {
        if (!hasAnyLocationPermission()) return null
        return try {
            client.lastLocation.awaitResult()?.toLatLng()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "lastLocation failed", e)
            null
        }
    }

    /** A fresh high-accuracy fix, falling back to the last known one. Used when dropping a zone. */
    @SuppressLint("MissingPermission") // guarded by hasFinePermission()
    suspend fun current(): LatLng? {
        if (!hasFinePermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .awaitResult()?.toLatLng() ?: lastKnown()
        } catch (e: CancellationException) {
            cancellation.cancel()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation failed", e)
            lastKnown()
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toLatLng() = LatLng(latitude, longitude)

    private companion object {
        const val TAG = "LocationProvider"
    }
}
