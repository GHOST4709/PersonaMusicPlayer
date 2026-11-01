package com.example.personabgm.domain.model

/** A geofence zone the person created (spec 5.1). `id` doubles as the geofence request id. */
data class Zone(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Float,
    /** Track id to play inside the zone, or null to use the active theme's default (spec 5.3 stage 1). */
    val assignedTrackId: String?,
    val enabled: Boolean,
    val createdAt: Long,
)

data class LatLng(val lat: Double, val lng: Double)
