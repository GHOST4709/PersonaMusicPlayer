package com.example.personabgm.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.io.IOException
import java.util.LinkedHashMap

/** Persistent LRU eviction with a live-adjustable limit and pins for loaded/incoming track IDs. */
@OptIn(UnstableApi::class)
class ConfigurableLruCacheEvictor(initialMaxBytes: Long) : CacheEvictor {
    private val lock = Any()
    private val lruSpans = LinkedHashMap<CacheSpan, Unit>()
    private var maxBytes = initialMaxBytes.coerceAtLeast(MIN_CACHE_BYTES)
    private var protectedKeys: Set<String> = emptySet()
    private val clearWhenUnprotected = mutableSetOf<String>()

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        synchronized(lock) { evictUntilWithinBudget(cache, length.coerceAtLeast(0L)) }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            lruSpans.remove(span)
            lruSpans[span] = Unit
            evictUntilWithinBudget(cache, 0L)
        }
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        synchronized(lock) { lruSpans.remove(span) }
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        synchronized(lock) {
            lruSpans.remove(oldSpan)
            lruSpans[newSpan] = Unit
        }
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    fun updateMaxBytes(cache: Cache?, bytes: Long) {
        synchronized(lock) {
            maxBytes = bytes.coerceAtLeast(MIN_CACHE_BYTES)
            if (cache != null) {
                rebuildFrom(cache)
                evictUntilWithinBudget(cache, 0L)
            }
        }
    }

    fun protectKeys(cache: Cache?, keys: Set<String>) {
        synchronized(lock) {
            protectedKeys = keys
            if (cache != null) {
                val released = clearWhenUnprotected - protectedKeys
                removeKeys(cache, released)
                clearWhenUnprotected.removeAll(released)
                evictUntilWithinBudget(cache, 0L)
            }
        }
    }

    fun clearUnprotected(cache: Cache) {
        synchronized(lock) {
            clearWhenUnprotected += protectedKeys
            removeKeys(cache, lruSpans.keys.map { it.key }.toSet() - protectedKeys)
        }
    }

    fun rebuildFrom(cache: Cache) {
        synchronized(lock) {
            lruSpans.clear()
            cache.keys.asSequence()
                .flatMap { key -> cache.getCachedSpans(key).asSequence() }
                .filter { it.isCached }
                .sortedBy { it.lastTouchTimestamp }
                .forEach { lruSpans[it] = Unit }
            evictUntilWithinBudget(cache, 0L)
        }
    }

    private fun removeKeys(cache: Cache, keys: Set<String>) {
        lruSpans.keys.filter { it.key in keys }.toList().forEach { span ->
            try { cache.removeSpan(span) } catch (_: IOException) { /* retry on a later trim */ }
        }
    }

    private fun evictUntilWithinBudget(cache: Cache, incomingBytes: Long) {
        while (cache.cacheSpace + incomingBytes > maxBytes) {
            val oldest = lruSpans.keys.firstOrNull { it.key !in protectedKeys } ?: return
            try {
                cache.removeSpan(oldest)
            } catch (_: IOException) {
                return
            }
        }
    }

    companion object {
        const val MIN_CACHE_BYTES = 256L * 1024 * 1024
    }
}
