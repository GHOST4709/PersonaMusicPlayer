package com.example.personabgm.context

import com.example.personabgm.domain.model.TimeBucket
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeBucketsTest {

    @Test fun boundariesFollowSpec() {
        assertEquals(TimeBucket.NIGHT, TimeBuckets.at(LocalTime.of(5, 59, 59)))
        assertEquals(TimeBucket.DAY, TimeBuckets.at(LocalTime.of(6, 0)))
        assertEquals(TimeBucket.DAY, TimeBuckets.at(LocalTime.of(12, 0)))
        assertEquals(TimeBucket.DAY, TimeBuckets.at(LocalTime.of(17, 59, 59)))
        assertEquals(TimeBucket.NIGHT, TimeBuckets.at(LocalTime.of(18, 0)))
        assertEquals(TimeBucket.NIGHT, TimeBuckets.at(LocalTime.of(23, 59, 59)))
        assertEquals(TimeBucket.NIGHT, TimeBuckets.at(LocalTime.MIDNIGHT))
    }

    private val utc = ZoneId.of("UTC")
    private fun at(h: Int, m: Int = 0, s: Int = 0, date: LocalDate = LocalDate.of(2026, 6, 15), zone: ZoneId = utc) =
        ZonedDateTime.of(date, LocalTime.of(h, m, s), zone)

    @Test fun nextBoundaryFromEachPartOfTheDay() {
        assertEquals(at(6), TimeBuckets.nextBoundary(at(5, 59)))
        assertEquals(at(18), TimeBuckets.nextBoundary(at(6, 0)))       // strictly after: 06:00 itself is not "next"
        assertEquals(at(18), TimeBuckets.nextBoundary(at(17, 59)))
        assertEquals(at(6, date = LocalDate.of(2026, 6, 16)), TimeBuckets.nextBoundary(at(18, 0)))
        assertEquals(at(6, date = LocalDate.of(2026, 6, 16)), TimeBuckets.nextBoundary(at(23, 0)))
        assertEquals(at(6), TimeBuckets.nextBoundary(at(0, 0)))
    }

    @Test fun millisUntilNextBoundary() {
        assertEquals(60_000L, TimeBuckets.millisUntilNextBoundary(at(5, 59)))
        assertEquals(12 * 3_600_000L, TimeBuckets.millisUntilNextBoundary(at(6, 0)))
    }

    @Test fun neverReturnsLessThanOneSecond() {
        val almost = at(5, 59, 59).plusNanos(999_000_000)
        assertEquals(1_000L, TimeBuckets.millisUntilNextBoundary(almost))
    }

    @Test fun springForwardDayUsesRealElapsedTime() {
        // New York, 2026-03-08: clocks jump 02:00 -> 03:00, so 01:30 to 06:00 is 3.5 real hours.
        val ny = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(LocalDate.of(2026, 3, 8), LocalTime.of(1, 30), ny)
        assertEquals((3.5 * 3_600_000).toLong(), TimeBuckets.millisUntilNextBoundary(now))
    }

    @Test fun fallBackDayUsesRealElapsedTime() {
        // New York, 2026-11-01: clocks repeat 01:00-02:00, so 00:30 to 06:00 is 6.5 real hours.
        val ny = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(LocalDate.of(2026, 11, 1), LocalTime.of(0, 30), ny)
        assertEquals((6.5 * 3_600_000).toLong(), TimeBuckets.millisUntilNextBoundary(now))
    }
}
