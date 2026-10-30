package com.example.personabgm.domain.model

/** Runtime time-of-day bucket: DAY is 06:00-18:00 local, NIGHT is 18:00-06:00 (spec 5.5). */
enum class TimeBucket { DAY, NIGHT }

/** Runtime weather after mapping WMO codes (spec 5.4). */
enum class WeatherType { CLEAR, RAIN, SNOW }

/** Manifest `time_of_day` tag. ALL matches any runtime [TimeBucket]. */
enum class TrackTime {
    ALL, DAY, NIGHT;

    companion object {
        /** Unknown or missing tags become ALL so a future value never breaks an older app (spec 7.1). */
        fun fromNameOrAll(name: String?): TrackTime = entries.firstOrNull { it.name == name } ?: ALL
    }
}

/** Manifest `weather` tag. ALL matches any runtime [WeatherType]. */
enum class TrackWeather {
    ALL, CLEAR, RAIN, SNOW;

    companion object {
        fun fromNameOrAll(name: String?): TrackWeather = entries.firstOrNull { it.name == name } ?: ALL
    }
}

/** Crossfade gain curve (spec 4.2). Equal-power avoids the mid-fade loudness dip of a linear fade. */
enum class CrossfadeCurve { EQUAL_POWER, LINEAR }

/** Persisted geofence state per zone (spec 7.2 `zone_state`). */
enum class ZoneStateValue { INSIDE, OUTSIDE }
