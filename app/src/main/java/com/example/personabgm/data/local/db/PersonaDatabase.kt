package com.example.personabgm.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ZoneEntity::class,
        ZoneStateEntity::class,
        ManifestTrackEntity::class,
        ManifestThemeDefaultEntity::class,
        ManifestMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PersonaDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun zoneStateDao(): ZoneStateDao
    abstract fun manifestDao(): ManifestDao
}
