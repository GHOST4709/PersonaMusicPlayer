package com.example.personabgm.context

import com.example.personabgm.domain.model.LatLng
import com.example.personabgm.domain.model.WeatherType
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Maps Open-Meteo WMO weather codes to the three runtime weather types (spec 5.4). */
object WeatherMapper {
    fun fromWmoCode(code: Int?): WeatherType = when (code) {
        in 51..57, // drizzle, freezing drizzle
        in 61..67, // rain, freezing rain
        in 80..82, // rain showers
        in 95..99, // thunderstorm (with or without hail)
        -> WeatherType.RAIN

        in 71..77, // snow fall, snow grains
        in 85..86, // snow showers
        -> WeatherType.SNOW

        // 0-3 clear to overcast, 45/48 fog, null and anything unknown
        else -> WeatherType.CLEAR
    }
}

/** A weather reading and where/when it was taken. */
data class WeatherSnapshot(
    val type: WeatherType,
    val fetchedAtMs: Long,
    val lat: Double,
    val lng: Double,
)

/**
 * When to fetch and when to distrust a reading (spec 5.4):
 *  - fetch at most every 20 minutes, or sooner after moving more than about 5 km;
 *  - a reading older than 3 hours counts as CLEAR, so a long outage can never leave rain music stuck on;
 *  - coordinates are rounded to 2 decimals (about 1 km) before they leave the device.
 */
object WeatherPolicy {
    const val MIN_INTERVAL_MS = 20 * 60 * 1000L
    const val MOVE_THRESHOLD_KM = 5.0
    const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L

    fun shouldRefresh(last: WeatherSnapshot?, location: LatLng, nowMs: Long): Boolean {
        if (last == null) return true
        val age = nowMs - last.fetchedAtMs
        if (age < 0) return true // the clock went backwards; do not trust the old reading
        if (age >= MIN_INTERVAL_MS) return true
        return distanceKm(LatLng(last.lat, last.lng), location) > MOVE_THRESHOLD_KM
    }

    /** The weather to act on right now: the stored reading, or CLEAR if there is none or it is stale. */
    fun effective(snapshot: WeatherSnapshot?, nowMs: Long): WeatherType {
        if (snapshot == null) return WeatherType.CLEAR
        val age = nowMs - snapshot.fetchedAtMs
        return if (age < 0 || age > STALE_AFTER_MS) WeatherType.CLEAR else snapshot.type
    }

    fun roundCoordinate(value: Double): Double = (value * 100.0).roundToLong() / 100.0

    /** Great-circle distance (haversine). */
    fun distanceKm(a: LatLng, b: LatLng): Double {
        val earthRadiusKm = 6371.0088
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * earthRadiusKm * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
