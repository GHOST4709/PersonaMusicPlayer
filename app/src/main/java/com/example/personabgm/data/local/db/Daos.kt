package com.example.personabgm.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones WHERE enabled = 1")
    suspend fun getEnabled(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun getById(id: String): ZoneEntity?

    @Query("SELECT COUNT(*) FROM zones")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ZoneStateDao {
    @Query("SELECT * FROM zone_state")
    fun observeAll(): Flow<List<ZoneStateEntity>>

    @Query("SELECT * FROM zone_state")
    suspend fun getAll(): List<ZoneStateEntity>

    @Upsert
    suspend fun upsert(state: ZoneStateEntity)

    @Query("DELETE FROM zone_state WHERE zone_id = :zoneId")
    suspend fun delete(zoneId: String)

    @Query("DELETE FROM zone_state")
    suspend fun clearAll()
}

/** Consistent read of the whole cached manifest (meta + tracks + theme defaults). */
data class ManifestSnapshot(
    val meta: ManifestMetaEntity,
    val tracks: List<ManifestTrackEntity>,
    val themeDefaults: List<ManifestThemeDefaultEntity>,
)

@Dao
abstract class ManifestDao {
    /** Emits whenever the cached manifest changes. Meta is always written last in [replaceAll]. */
    @Query("SELECT * FROM manifest_meta WHERE id = 1")
    abstract fun observeMeta(): Flow<ManifestMetaEntity?>

    // ORDER BY rowid preserves the manifest's own track order (used for Next/Previous).
    @Query("SELECT * FROM manifest_tracks ORDER BY rowid")
    abstract suspend fun getTracks(): List<ManifestTrackEntity>

    @Query("SELECT * FROM manifest_theme_defaults")
    abstract suspend fun getThemeDefaults(): List<ManifestThemeDefaultEntity>

    @Query("SELECT * FROM manifest_meta WHERE id = 1")
    abstract suspend fun getMeta(): ManifestMetaEntity?

    @Query("DELETE FROM manifest_tracks")
    abstract suspend fun clearTracks()

    @Query("DELETE FROM manifest_theme_defaults")
    abstract suspend fun clearThemeDefaults()

    @Upsert
    abstract suspend fun upsertTracks(tracks: List<ManifestTrackEntity>)

    @Upsert
    abstract suspend fun upsertThemeDefaults(defaults: List<ManifestThemeDefaultEntity>)

    @Upsert
    abstract suspend fun upsertMeta(meta: ManifestMetaEntity)

    /** Atomically replaces the cached manifest so readers never see a half-written copy. */
    @Transaction
    open suspend fun replaceAll(
        tracks: List<ManifestTrackEntity>,
        themeDefaults: List<ManifestThemeDefaultEntity>,
        meta: ManifestMetaEntity,
    ) {
        clearTracks()
        clearThemeDefaults()
        upsertTracks(tracks)
        upsertThemeDefaults(themeDefaults)
        upsertMeta(meta)
    }

    @Transaction
    open suspend fun loadSnapshot(): ManifestSnapshot? {
        val meta = getMeta() ?: return null
        return ManifestSnapshot(meta, getTracks(), getThemeDefaults())
    }
}
