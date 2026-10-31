package com.example.personabgm.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.personabgm.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * The one audio cache (spec 4.4): a 500 MB LRU [SimpleCache] wrapped around HTTP.
 *
 *  - There must be exactly one [SimpleCache] per directory, hence this singleton.
 *  - Files live in `filesDir`, not `cacheDir`, so the OS cannot purge them under storage pressure.
 *  - The track id is the cache key, so changing a CDN or signed URL never invalidates audio.
 */
@OptIn(UnstableApi::class)
@Singleton
class MediaCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val cache: SimpleCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SimpleCache(
            File(context.filesDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("PersonaBGM/0.1")
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(false)

    /** Reads from the cache first, falls back to HTTP, and stores what it reads. */
    val dataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** True when every byte of the track is on disk (needs the content length, learned on first read). */
    fun isFullyCached(track: Track): Boolean {
        val known = ContentMetadata.getContentLength(cache.getContentMetadata(track.id))
        val length = if (known != C.LENGTH_UNSET.toLong()) known else (track.sizeBytes ?: return false)
        return length > 0 && cache.isCached(track.id, 0, length)
    }

    /** Downloads the whole track into the cache, resuming any partial copy. Throws IOException on failure. */
    suspend fun download(track: Track) {
        val spec = DataSpec.Builder().setUri(track.streamUrl).setKey(track.id).build()
        val writer = CacheWriter(dataSourceFactory.createDataSource(), spec, null, null)
        try {
            runInterruptible(Dispatchers.IO) { writer.cache() }
        } catch (e: CancellationException) {
            writer.cancel()
            throw e
        }
    }

    fun totalBytes(): Long = cache.cacheSpace

    fun clear() {
        cache.keys.toList().forEach { cache.removeResource(it) }
    }

    companion object {
        const val MAX_BYTES = 500L * 1024 * 1024
    }
}
