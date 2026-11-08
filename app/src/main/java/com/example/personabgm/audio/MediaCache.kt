package com.example.personabgm.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.example.personabgm.BuildConfig
import com.example.personabgm.data.local.datastore.SettingsRepository
import com.example.personabgm.domain.model.Track
import com.example.personabgm.security.MusicAccessTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/** Persistent streaming cache. Only bytes requested from the private Worker are kept locally. */
@OptIn(UnstableApi::class)
@Singleton
class MediaCache @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
    private val accessTokenStore: MusicAccessTokenStore,
) {
    private val evictor = ConfigurableLruCacheEvictor(DEFAULT_MAX_BYTES)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var cacheInstance: SimpleCache? = null

    val cache: SimpleCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SimpleCache(
            File(context.filesDir, "media_cache"),
            evictor,
            StandaloneDatabaseProvider(context),
        ).also { created ->
            cacheInstance = created
            evictor.rebuildFrom(created)
        }
    }

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("PersonaBGM/0.7")
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(false)

    private val authenticatedUpstreamFactory: DataSource.Factory by lazy {
        ResolvingDataSource.Factory(
            httpFactory,
            ResolvingDataSource.Resolver { dataSpec ->
                if (!isConfiguredWorkerAudioUrl(dataSpec.uri)) {
                    throw IOException("Audio URL is not on the configured private music Worker.")
                }
                val token = accessTokenStore.getToken()
                    ?: throw IOException("Private music access is not configured. Add the access token in Settings.")
                val headers = dataSpec.httpRequestHeaders.toMutableMap()
                headers["Authorization"] = "Bearer $token"
                dataSpec.buildUpon().setHttpRequestHeaders(headers).build()
            },
        )
    }

    val dataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(authenticatedUpstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    init {
        // The first DataStore emission also updates the evictor before SimpleCache is constructed.
        scope.launch {
            settingsRepository.settings.collect { settings ->
                evictor.updateMaxBytes(cacheInstance, settings.audioCacheLimitMb.toLong() * BYTES_PER_MB)
            }
        }
    }

    private fun isConfiguredWorkerAudioUrl(uri: Uri): Boolean {
        val api = Uri.parse(BuildConfig.MUSIC_API_BASE_URL)
        val expectedPort = api.port.takeIf { it >= 0 } ?: 443
        val actualPort = uri.port.takeIf { it >= 0 } ?: 443
        val path = uri.encodedPath.orEmpty()
        return uri.scheme.equals("https", true) &&
            uri.host.equals(api.host, true) && actualPort == expectedPort &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            path.startsWith("/v1/audio/") && path.length > "/v1/audio/".length
    }

    fun protectPlaybackTracks(trackIds: Set<String>) = evictor.protectKeys(cacheInstance, trackIds)

    fun isFullyCached(track: Track): Boolean {
        val known = ContentMetadata.getContentLength(cache.getContentMetadata(track.id))
        val length = if (known != C.LENGTH_UNSET.toLong()) known else (track.sizeBytes ?: return false)
        return length > 0 && cache.isCached(track.id, 0, length)
    }

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

    /** Clears saved audio but defers removal of currently loaded/crossfading tracks. */
    fun clear() = evictor.clearUnprotected(cache)

    companion object {
        const val DEFAULT_CACHE_LIMIT_MB = 1024
        const val DEFAULT_MAX_BYTES = DEFAULT_CACHE_LIMIT_MB.toLong() * 1024 * 1024
        const val MIN_CACHE_LIMIT_MB = 256
        const val MAX_CACHE_LIMIT_MB = 2048
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
