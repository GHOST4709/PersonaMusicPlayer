package com.example.personabgm.data.repository

import com.example.personabgm.data.local.db.ManifestSnapshot
import com.example.personabgm.data.local.db.ManifestThemeDefaultEntity
import com.example.personabgm.data.local.db.ManifestTrackEntity
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.Track

internal fun Track.toEntity() = ManifestTrackEntity(
    id = id,
    gameTheme = gameTheme,
    title = title,
    artist = artist,
    timeOfDay = timeOfDay,
    weather = weather,
    variantGroup = variantGroup,
    streamUrl = streamUrl,
    sizeBytes = sizeBytes,
)

internal fun ManifestTrackEntity.toDomain() = Track(
    id = id,
    gameTheme = gameTheme,
    title = title,
    artist = artist,
    timeOfDay = timeOfDay,
    weather = weather,
    variantGroup = variantGroup,
    streamUrl = streamUrl,
    sizeBytes = sizeBytes,
)

internal fun Manifest.themeDefaultEntities(): List<ManifestThemeDefaultEntity> =
    themeDefaults.map { (theme, trackId) -> ManifestThemeDefaultEntity(gameTheme = theme, trackId = trackId) }

internal fun ManifestSnapshot.toManifest() = Manifest(
    version = meta.version,
    themeDefaults = themeDefaults.associate { it.gameTheme to it.trackId },
    tracks = tracks.map { it.toDomain() },
)
