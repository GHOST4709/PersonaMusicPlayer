package com.example.personabgm.audio

import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverrideNavigatorTest {

    private fun t(id: String, theme: GameTheme = GameTheme.PERSONA_3_RELOAD) = Track(
        id = id, gameTheme = theme, title = id, artist = "A", timeOfDay = TrackTime.ALL,
        weather = TrackWeather.ALL, variantGroup = null, streamUrl = "https://x.example/$id.m4a", sizeBytes = null,
    )

    private val a = t("a"); private val b = t("b"); private val c = t("c")
    private val pool = listOf(a, b, c)
    private val all = (pool + t("far", GameTheme.PERSONA_5_ROYAL)).associateBy { it.id }
    private val lookup: (String) -> Track? = { all[it] }

    private fun nav() = OverrideNavigator()

    @Test fun nextFollowsManifestOrderAndWraps() {
        val n = nav()
        assertEquals("b", n.next("a", pool)?.id)
        assertEquals("c", n.next("b", pool)?.id)
        assertEquals("a", n.next("c", pool)?.id)
    }

    @Test fun nextStartsAtPoolFirstWhenCurrentIsNotInPoolOrNull() {
        assertEquals("a", nav().next("far", pool)?.id)
        assertEquals("a", nav().next(null, pool)?.id)
    }

    @Test fun emptyPoolYieldsNull() {
        assertNull(nav().next("a", emptyList()))
        assertNull(nav().previous("a", emptyList(), lookup))
    }

    @Test fun previousWalksBackThroughHistory() {
        val n = nav()
        n.next("a", pool)          // a -> b, history [a]
        n.next("b", pool)          // b -> c, history [a, b]
        assertEquals("b", n.previous("c", pool, lookup)?.id)
        assertEquals("a", n.previous("b", pool, lookup)?.id)
    }

    @Test fun previousWithoutHistoryStepsBackInPoolOrderAndWraps() {
        val n = nav()
        assertEquals("a", n.previous("b", pool, lookup)?.id)
        assertEquals("c", n.previous("a", pool, lookup)?.id)   // wraps to last
        assertEquals("c", n.previous("far", pool, lookup)?.id) // not in pool -> last
    }

    @Test fun historyMayHoldTracksOutsideThePool() {
        val n = nav()
        n.recordManualPlay("far", "a")   // person picked "a" from Library while "far" (other theme) played
        assertEquals("far", n.previous("a", pool, lookup)?.id)
    }

    @Test fun unknownHistoryIdsAreSkipped() {
        val n = nav()
        n.recordManualPlay("ghost", "b")      // ghost is not resolvable
        n.recordManualPlay("a", "c")
        assertEquals("a", n.previous("c", pool, lookup)?.id)
        // Only "ghost" is left in history and it cannot be resolved, so it is skipped and the
        // navigator falls back to pool order: before "a" (the first track) wraps to the last, "c".
        assertEquals("c", n.previous("a", pool, lookup)?.id)
    }

    @Test fun clearForgetsHistory() {
        val n = nav()
        n.next("a", pool); n.next("b", pool)
        n.clear()
        // No history: previous of "c" is "b" by pool order, and it must not come from history.
        assertEquals("b", n.previous("c", pool, lookup)?.id)
    }

    @Test fun singleTrackPoolNextReturnsTheSameTrackAndRecordsNothing() {
        val n = nav()
        assertEquals("a", n.next("a", listOf(a))?.id)
        assertEquals("a", n.previous("a", listOf(a), lookup)?.id)
    }

    @Test fun historyIsBounded() {
        val n = OverrideNavigator(maxHistory = 3)
        val ids = ('a'..'h').map { it.toString() }
        val big = ids.map { t(it) }
        val look: (String) -> Track? = { id -> big.firstOrNull { it.id == id } }
        for (i in 0 until ids.size - 1) n.recordManualPlay(ids[i], ids[i + 1])
        // Seven departures (a..g) were recorded but only the newest three (e, f, g) are kept,
        // so walking back from "h" yields g, f, e and then runs out of history.
        val back = generateSequence(ids.last()) { cur -> n.previous(cur, big, look)?.id }.take(4).toList()
        assertEquals(listOf("h", "g", "f", "e"), back)
    }
}
