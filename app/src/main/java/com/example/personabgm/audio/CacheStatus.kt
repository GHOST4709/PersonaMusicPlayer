package com.example.personabgm.audio

import com.example.personabgm.domain.model.Manifest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class CacheSnapshot(
    val cachedTrackIds: Set<String> = emptySet(),
    val totalBytes: Long = 0L,
)

/** Observable view of what is in the audio cache, for the Library "Saved" chips and Settings. */
@Singleton
class CacheStatus @Inject constructor(
    private val mediaCache: MediaCache,
) {
    private val _snapshot = MutableStateFlow(CacheSnapshot())
    val snapshot: StateFlow<CacheSnapshot> = _snapshot.asStateFlow()

    suspend fun refresh(manifest: Manifest?) {
        _snapshot.value = withContext(Dispatchers.IO) {
            val ids = manifest?.tracks.orEmpty().filter { mediaCache.isFullyCached(it) }.map { it.id }.toSet()
            CacheSnapshot(cachedTrackIds = ids, totalBytes = mediaCache.totalBytes())
        }
    }

    suspend fun clearAll(manifest: Manifest?) {
        withContext(Dispatchers.IO) { mediaCache.clear() }
        refresh(manifest)
    }
}
