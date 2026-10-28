package com.example.personabgm.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import com.example.personabgm.domain.model.ZoneStateValue

/** A user-created geofence zone. `id` is also the geofence `requestId`. */
@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** Default 150 m, minimum 100 m (spec 5.1). Validation happens in the repository. */
    @ColumnInfo(name = "radius_m") val radiusM: Float = DEFAULT_RADIUS_M,
    @ColumnInfo(name = "assigned_track_id") val assignedTrackId: String? = null,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        const val DEFAULT_RADIUS_M = 150f
        const val MIN_RADIUS_M = 100f
        const val MAX_RADIUS_M = 500f
        const val MAX_ZONES = 100 // Android limit: 100 geofences per app per device user
    }
}

/** Persisted geofence transitions so state survives process death (spec 5.6). */
@Entity(
    tableName = "zone_state",
    foreignKeys = [
        ForeignKey(
            entity = ZoneEntity::class,
            parentColumns = ["id"],
            childColumns = ["zone_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ZoneStateEntity(
    @PrimaryKey @ColumnInfo(name = "zone_id") val zoneId: String,
    val state: ZoneStateValue,
    @ColumnInfo(name = "since_epoch_ms") val sinceEpochMs: Long,
)

/** Cached copy of a manifest track so the app works offline on a cold start (spec 7.1). */
@Entity(tableName = "manifest_tracks")
data class ManifestTrackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_theme") val gameTheme: GameTheme,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "time_of_day") val timeOfDay: TrackTime,
    val weather: TrackWeather,
    @ColumnInfo(name = "variant_group") val variantGroup: String?,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
)

/** Cached `theme_defaults` map from the manifest (one row per theme). */
@Entity(tableName = "manifest_theme_defaults")
data class ManifestThemeDefaultEntity(
    @PrimaryKey @ColumnInfo(name = "game_theme") val gameTheme: GameTheme,
    @ColumnInfo(name = "track_id") val trackId: String,
)

/** Single-row manifest metadata (id is always 1). */
@Entity(tableName = "manifest_meta")
data class ManifestMetaEntity(
    @PrimaryKey val id: Int = 1,
    val version: Int,
    val etag: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
