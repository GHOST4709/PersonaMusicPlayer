package com.example.personabgm.context

import com.example.personabgm.domain.model.TimeBucket
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Day is 06:00 to 18:00 local time, Night is 18:00 to 06:00 (spec 5.5). */
object TimeBuckets {
    val DAY_START: LocalTime = LocalTime.of(6, 0)
    val NIGHT_START: LocalTime = LocalTime.of(18, 0)

    fun at(time: LocalTime): TimeBucket =
        if (!time.isBefore(DAY_START) && time.isBefore(NIGHT_START)) TimeBucket.DAY else TimeBucket.NIGHT

    /** The next 06:00 or 18:00 strictly after [now], in [now]'s own zone (so DST shifts are honored). */
    fun nextBoundary(now: ZonedDateTime): ZonedDateTime {
        val today: LocalDate = now.toLocalDate()
        val candidates = listOf(
            today.atTime(DAY_START),
            today.atTime(NIGHT_START),
            today.plusDays(1).atTime(DAY_START),
        )
        return candidates
            .map { it.atZone(now.zone) }
            .first { it.isAfter(now) }
    }

    fun millisUntilNextBoundary(now: ZonedDateTime): Long =
        (nextBoundary(now).toInstant().toEpochMilli() - now.toInstant().toEpochMilli()).coerceAtLeast(1_000L)
}
