package com.example.personabgm.data.repository

import android.content.Context
import android.util.Log
import com.example.personabgm.BuildConfig
import com.example.personabgm.data.local.db.ManifestDao
import com.example.personabgm.data.local.db.ManifestMetaEntity
import com.example.personabgm.data.remote.IssueSeverity
import com.example.personabgm.data.remote.ManifestApi
import com.example.personabgm.data.remote.ManifestDto
import com.example.personabgm.data.remote.ManifestIssue
import com.example.personabgm.data.remote.ManifestValidator
import com.example.personabgm.domain.model.Manifest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface RefreshResult {
    /** A new manifest was validated and stored. [issues] lists repairs and dropped tracks. */
    data class Updated(val issues: List<ManifestIssue>) : RefreshResult

    /** Server answered 304: the cached manifest is current. */
    data object NotModified : RefreshResult

    /** The response could not be used. The previous cached manifest (if any) is untouched. */
    data class Invalid(val issues: List<ManifestIssue>) : RefreshResult

    data class HttpError(val code: Int) : RefreshResult
    data class NetworkError(val cause: IOException) : RefreshResult
}

/**
 * Single source of truth for the track manifest (spec 7.1).
 *
 * Reads always come from Room, so the app works fully offline after the first successful load.
 * [refresh] is stale-while-revalidate: it never clears the cache unless a new, valid manifest
 * has been received.
 */
@Singleton
class ManifestRepository @Inject constructor(
    private val api: ManifestApi,
    private val dao: ManifestDao,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {
    /** Cached manifest, or null when nothing has been stored yet. */
    val manifest: Flow<Manifest?> = dao.observeMeta().map { meta ->
        if (meta == null) null else dao.loadSnapshot()?.toManifest()
    }

    suspend fun refresh(): RefreshResult {
        val cachedMeta = dao.getMeta()

        val result: RefreshResult = try {
            fetchAndStore(cachedMeta)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            RefreshResult.NetworkError(e)
        } catch (e: SerializationException) {
            RefreshResult.Invalid(listOf(ManifestIssue(IssueSeverity.ERROR, "Manifest is not valid JSON: ${e.message}")))
        } catch (e: IllegalArgumentException) {
            RefreshResult.Invalid(listOf(ManifestIssue(IssueSeverity.ERROR, "Manifest could not be decoded: ${e.message}")))
        }

        val failed = result is RefreshResult.NetworkError ||
            result is RefreshResult.HttpError ||
            result is RefreshResult.Invalid
        if (failed && cachedMeta == null && BuildConfig.DEBUG) seedFromAssets()

        return result
    }

    private suspend fun fetchAndStore(cachedMeta: ManifestMetaEntity?): RefreshResult {
        val response = api.fetchManifest(BuildConfig.MANIFEST_URL, cachedMeta?.etag)

        if (response.code() == 304 && cachedMeta != null) {
            dao.upsertMeta(cachedMeta.copy(fetchedAt = System.currentTimeMillis()))
            return RefreshResult.NotModified
        }
        if (!response.isSuccessful) return RefreshResult.HttpError(response.code())

        val body = response.body()
            ?: return RefreshResult.Invalid(listOf(ManifestIssue(IssueSeverity.ERROR, "Empty response body.")))

        val validation = ManifestValidator.validate(body)
        logIssues(validation.issues)
        val manifest = validation.manifest ?: return RefreshResult.Invalid(validation.issues)

        store(manifest, etag = response.headers()["ETag"])
        return RefreshResult.Updated(validation.issues)
    }

    private suspend fun store(manifest: Manifest, etag: String?) {
        dao.replaceAll(
            tracks = manifest.tracks.map { it.toEntity() },
            themeDefaults = manifest.themeDefaultEntities(),
            meta = ManifestMetaEntity(
                version = manifest.version,
                etag = etag,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Debug builds only: when the very first fetch fails (placeholder host, no network), load the
     * bundled sample so UI and playback work can be developed without a server. Release builds
     * never do this: an empty cache stays empty and the UI shows the error state.
     */
    private suspend fun seedFromAssets() {
        try {
            val text = withContext(Dispatchers.IO) {
                context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
            }
            val validation = ManifestValidator.validate(json.decodeFromString(ManifestDto.serializer(), text))
            logIssues(validation.issues)
            validation.manifest?.let { store(it, etag = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Debug seed manifest could not be loaded", e)
        }
    }

    private fun logIssues(issues: List<ManifestIssue>) {
        issues.forEach {
            if (it.severity == IssueSeverity.ERROR) Log.e(TAG, it.message) else Log.w(TAG, it.message)
        }
    }

    private companion object {
        const val TAG = "ManifestRepository"
        const val SEED_ASSET = "manifest_seed.json"
    }
}
