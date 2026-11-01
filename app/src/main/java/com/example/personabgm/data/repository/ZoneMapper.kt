package com.example.personabgm.data.repository

import com.example.personabgm.data.local.db.ZoneEntity
import com.example.personabgm.domain.model.Zone

internal fun ZoneEntity.toDomain() = Zone(
    id = id,
    name = name,
    lat = lat,
    lng = lng,
    radiusM = radiusM,
    assignedTrackId = assignedTrackId,
    enabled = enabled,
    createdAt = createdAt,
)
