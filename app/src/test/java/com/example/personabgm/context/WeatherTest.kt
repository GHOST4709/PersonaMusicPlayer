package com.example.personabgm.context

import com.example.personabgm.domain.model.LatLng
import com.example.personabgm.domain.model.WeatherType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WeatherTest {

    // ---- WMO mapping (spec 5.4 table) ----

    @Test fun clearCloudyAndFogAreClear() {
        listOf(0, 1, 2, 3, 45, 48).forEach { assertEquals("code $it", WeatherType.CLEAR, WeatherMapper.fromWmoCode(it)) }
    }

    @Test fun drizzleRainShowersAndThunderstormAreRain() {
        listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
            .forEach { assertEquals("code $it", WeatherType.RAIN, WeatherMapper.fromWmoCode(it)) }
    }

    @Test fun snowAndSnowShowersAreSnow() {
        listOf(71, 73, 75, 77, 85, 86).forEach { assertEquals("code $it", WeatherType.SNOW, WeatherMapper.fromWmoCode(it)) }
    }

    @Test fun unknownOrMissingCodesAreClear() {
        listOf(null, -1, 4, 44, 58, 70, 100, 999).forEach { assertEquals("code $it", WeatherType.CLEAR, WeatherMapper.fromWmoCode(it)) }
    }

    // ---- refresh policy ----

    private val here = LatLng(48.8566, 2.3522)
    private fun snap(ageMs: Long, now: Long = 10_000_000_000L, at: LatLng = here, type: WeatherType = WeatherType.RAIN) =
        WeatherSnapshot(type, now - ageMs, at.lat, at.lng)
    private val now = 10_000_000_000L

    @Test fun firstFetchAlwaysHappens() = assertTrue(WeatherPolicy.shouldRefresh(null, here, now))

    @Test fun aFreshReadingInTheSamePlaceIsNotRefetched() =
        assertFalse(WeatherPolicy.shouldRefresh(snap(5 * 60_000L), here, now))

    @Test fun aReadingTwentyMinutesOldIsRefetched() {
        assertFalse(WeatherPolicy.shouldRefresh(snap(WeatherPolicy.MIN_INTERVAL_MS - 1), here, now))
        assertTrue(WeatherPolicy.shouldRefresh(snap(WeatherPolicy.MIN_INTERVAL_MS), here, now))
    }

    @Test fun movingMoreThanFiveKmRefetchesEvenWhenFresh() {
        val farAway = LatLng(48.8566 + 0.06, 2.3522)    // about 6.7 km north
        val nearby = LatLng(48.8566 + 0.03, 2.3522)     // about 3.3 km north
        assertTrue(WeatherPolicy.shouldRefresh(snap(60_000L), farAway, now))
        assertFalse(WeatherPolicy.shouldRefresh(snap(60_000L), nearby, now))
    }

    @Test fun aClockThatWentBackwardsForcesARefresh() =
        assertTrue(WeatherPolicy.shouldRefresh(snap(-5_000L), here, now))

    // ---- staleness ----

    @Test fun noReadingMeansClear() = assertEquals(WeatherType.CLEAR, WeatherPolicy.effective(null, now))

    @Test fun aRecentReadingIsUsed() = assertEquals(WeatherType.RAIN, WeatherPolicy.effective(snap(60_000L), now))

    @Test fun aReadingBecomesClearAfterThreeHours() {
        assertEquals(WeatherType.RAIN, WeatherPolicy.effective(snap(WeatherPolicy.STALE_AFTER_MS), now))
        assertEquals(WeatherType.CLEAR, WeatherPolicy.effective(snap(WeatherPolicy.STALE_AFTER_MS + 1), now))
    }

    @Test fun aReadingFromTheFutureIsNotTrusted() =
        assertEquals(WeatherType.CLEAR, WeatherPolicy.effective(snap(-1_000L), now))

    // ---- privacy rounding and distance ----

    @Test fun coordinatesAreRoundedToTwoDecimals() {
        assertEquals(12.35, WeatherPolicy.roundCoordinate(12.3456), 1e-9)
        assertEquals(-33.87, WeatherPolicy.roundCoordinate(-33.8688), 1e-9)
        assertEquals(0.0, WeatherPolicy.roundCoordinate(0.004), 1e-9)
        assertEquals(48.86, WeatherPolicy.roundCoordinate(48.8566), 1e-9)
    }

    @Test fun distanceMatchesKnownValues() {
        val oneDegreeOfLatitude = WeatherPolicy.distanceKm(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        assertTrue("was $oneDegreeOfLatitude", abs(oneDegreeOfLatitude - 111.19) < 0.5)
        val parisToLondon = WeatherPolicy.distanceKm(LatLng(48.8566, 2.3522), LatLng(51.5074, -0.1278))
        assertTrue("was $parisToLondon", abs(parisToLondon - 343.5) < 3.0)
        assertEquals(0.0, WeatherPolicy.distanceKm(here, here), 1e-9)
    }
}
