package com.example.personabgm.data.repository

import android.util.Log
import com.example.personabgm.context.WeatherMapper
import com.example.personabgm.context.WeatherPolicy
import com.example.personabgm.context.WeatherSnapshot
import com.example.personabgm.data.remote.WeatherApi
import com.example.personabgm.domain.model.LatLng
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the latest weather reading. The decisions (when to fetch, when a reading is stale, how to round
 * coordinates) live in [WeatherPolicy]; this class only does the I/O.
 *
 * Offline or failing fetches keep the previous reading; [WeatherPolicy.effective] turns it into CLEAR
 * once it is more than 3 hours old, so rain music can never get stuck on (spec 5.4).
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val api: WeatherApi,
) {
    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    private var lastAttemptMs = 0L

    /** Returns true if a new reading was stored. */
    suspend fun refreshIfNeeded(location: LatLng, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!WeatherPolicy.shouldRefresh(_snapshot.value, location, nowMs)) return false
        // After a failure, do not retry more than once a minute (zone changes and the timer can both ask).
        if (nowMs - lastAttemptMs in 0 until MIN_RETRY_GAP_MS) return false
        lastAttemptMs = nowMs

        // Only rounded coordinates (about 1 km) leave the device.
        val lat = WeatherPolicy.roundCoordinate(location.lat)
        val lng = WeatherPolicy.roundCoordinate(location.lng)
        return try {
            val response = api.currentWeather(lat, lng, "weather_code", "auto")
            _snapshot.value = WeatherSnapshot(
                type = WeatherMapper.fromWmoCode(response.current?.weatherCode),
                fetchedAtMs = nowMs,
                lat = lat,
                lng = lng,
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed; keeping the previous reading", e)
            false
        }
    }

    private companion object {
        const val TAG = "WeatherRepository"
        const val MIN_RETRY_GAP_MS = 60_000L
    }
}
