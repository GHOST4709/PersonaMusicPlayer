package com.example.personabgm.audio

import com.example.personabgm.domain.model.Track

/**
 * Next/Previous semantics for Manual Override (spec 4.5).
 *
 *  - [next] walks the active theme's pool in manifest order, wrapping at the end.
 *    If the current track is not in the pool, it starts at the pool's first track.
 *  - [previous] walks back through the history of tracks left by Next/Previous/Play. With no usable
 *    history it steps back through the pool in manifest order, wrapping at the start.
 *
 * History is in memory only and is cleared by [clear] (Resume Auto).
 */
class OverrideNavigator(private val maxHistory: Int = 50) {

    private val history = ArrayDeque<String>()

    fun next(currentId: String?, pool: List<Track>): Track? {
        if (pool.isEmpty()) return null
        val index = pool.indexOfFirst { it.id == currentId }
        val target = if (index < 0) pool.first() else pool[(index + 1) % pool.size]
        recordLeaving(currentId, target.id)
        return target
    }

    /** [lookup] resolves a history id to a Track (history may hold tracks outside [pool]). */
    fun previous(currentId: String?, pool: List<Track>, lookup: (String) -> Track?): Track? {
        while (history.isNotEmpty()) {
            val id = history.removeLast()
            if (id == currentId) continue
            val track = lookup(id)
            if (track != null) return track
        }
        if (pool.isEmpty()) return null
        val index = pool.indexOfFirst { it.id == currentId }
        return if (index <= 0) pool.last() else pool[index - 1]
    }

    /** Call when a specific track is chosen directly (for example from the Library). */
    fun recordManualPlay(currentId: String?, targetId: String) = recordLeaving(currentId, targetId)

    fun clear() = history.clear()

    private fun recordLeaving(currentId: String?, targetId: String) {
        if (currentId == null || currentId == targetId) return
        if (history.lastOrNull() == currentId) return
        history.addLast(currentId)
        while (history.size > maxHistory) history.removeFirst()
    }
}
