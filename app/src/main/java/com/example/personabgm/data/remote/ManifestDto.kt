package com.example.personabgm.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of the track manifest (schema v2, spec 7.1).
 *
 * Every field is optional and every enum is a plain String on purpose: any JSON shape decodes,
 * and [ManifestValidator] decides what is usable and reports exactly what it dropped and why.
 */
@Serializable
data class ManifestDto(
    val version: Int? = null,
    @SerialName("theme_defaults") val themeDefaults: Map<String, String>? = null,
    val tracks: List<TrackDto>? = null,
)

@Serializable
data class TrackDto(
    val id: String? = null,
    @SerialName("game_theme") val gameTheme: String? = null,
    val title: String? = null,
    val artist: String? = null,
    @SerialName("time_of_day") val timeOfDay: String? = null,
    val weather: String? = null,
    @SerialName("variant_group") val variantGroup: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
)
