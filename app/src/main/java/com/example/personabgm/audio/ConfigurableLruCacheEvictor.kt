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
    /**
     * Protects only the in-memory LRU bookkeeping. Never call into [Cache] while holding this lock.
     * SimpleCache invokes CacheEvictor callbacks while holding its own internal lock, so calling
     * Cache methods under this lock creates the lock-order inversion that can deadlock playback.
     */
    private val lock = Any()
    private val lruSpans = LinkedHashMap<CacheSpan, Unit>()
    private val evictingSpans = HashSet<CacheSpan>()
    private var maxBytes = initialMaxBytes.coerceAtLeast(MIN_CACHE_BYTES)
    private var protectedKeys: Set<String> = emptySet()
    private val clearWhenUnprotected = mutableSetOf<String>()

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        // Called by SimpleCache. It is safe to call removeSpan here as SimpleCache's monitor is
        // re-entrant for this same callback thread, and we do not hold our bookkeeping lock.
        evictUntilWithinBudget(cache, length.coerceAtLeast(0L))
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            lruSpans.remove(span)
            evictingSpans.remove(span)
            lruSpans[span] = Unit
        }
        // Do not move this into the synchronized block: removeSpan calls back into this evictor.
        evictUntilWithinBudget(cache, 0L)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            lruSpans.remove(span)
            evictingSpans.remove(span)
        }
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        synchronized(lock) {
            lruSpans.remove(oldSpan)
            evictingSpans.remove(oldSpan)
            lruSpans[newSpan] = Unit
        }
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    fun updateMaxBytes(cache: Cache?, bytes: Long) {
        synchronized(lock) {
            maxBytes = bytes.coerceAtLeast(MIN_CACHE_BYTES)
        }
        // Cache access must happen after releasing the evictor lock.
        cache?.let { evictUntilWithinBudget(it, 0L) }
    }

    fun protectKeys(cache: Cache?, keys: Set<String>) {
        val spansToClear = synchronized(lock) {
            protectedKeys = keys.toSet()
            if (cache == null) {
                emptyList()
            } else {
                val released = clearWhenUnprotected - protectedKeys
                clearWhenUnprotected.removeAll(released)
                lruSpans.keys.filter { it.key in released }
            }
        }

        if (cache != null) {
            removeSpans(cache, spansToClear)
            evictUntilWithinBudget(cache, 0L)
        }
    }

    fun clearUnprotected(cache: Cache) {
        val spansToClear = synchronized(lock) {
            clearWhenUnprotected += protectedKeys
            lruSpans.keys.filter { it.key !in protectedKeys }
        }
        removeSpans(cache, spansToClear)
    }

    /**
     * Removes a snapshot of spans without holding the bookkeeping lock. Cache callbacks update the
     * LRU map as each span is removed.
     */
    private fun removeSpans(cache: Cache, spans: List<CacheSpan>) {
        spans.forEach { span ->
            try {
                cache.removeSpan(span)
            } catch (_: IOException) {
                // A concurrent cache operation may already have removed this span. The normal
                // callback updates the map; a later trim can retry anything that remains.
            }
        }
    }

    /**
     * Evicts oldest unprotected spans until the budget is met. Cache reads/removals happen outside
     * [lock]; the selected-span set prevents concurrent trimmers selecting the same span twice.
     */
    private fun evictUntilWithinBudget(cache: Cache, incomingBytes: Long) {
        while (true) {
            // Read Cache first, with no evictor lock held. A CacheEvictor callback may already be
            // running under SimpleCache's internal monitor on this same thread.
            val currentBytes = cache.cacheSpace
            val budget = synchronized(lock) { maxBytes }
            if (currentBytes + incomingBytes <= budget) return

            val victim = synchronized(lock) {
                lruSpans.keys.firstOrNull { span ->
                    span.key !in protectedKeys && span !in evictingSpans
                }?.also { evictingSpans += it }
            } ?: return // All cached tracks are currently protected.

            try {
                cache.removeSpan(victim)
            } catch (_: IOException) {
                synchronized(lock) { evictingSpans.remove(victim) }
                return
            } finally {
                // onSpanRemoved normally clears this too; the finally also covers stale spans.
                synchronized(lock) { evictingSpans.remove(victim) }
            }
        }
    }

    companion object {
        const val MIN_CACHE_BYTES = 256L * 1024 * 1024
    }
}
