package com.example.personabgm.data.remote

import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.TrackWeather
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the real kotlinx-serialization decoding with the same Json settings as NetworkModule. */
class ManifestJsonParsingTest {

    private fun <T : Any> notNull(value: T?): T = value ?: throw AssertionError("expected a non-null value")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun parse(text: String) = ManifestValidator.validate(json.decodeFromString(ManifestDto.serializer(), text))

    @Test
    fun correctedV2SampleParsesCleanly() {
        val result = parse(
            """
            {
              "version": 2,
              "theme_defaults": {
                "PERSONA_3_RELOAD": "p3_color_your_night",
                "PERSONA_4_REVIVAL": "p4_roaming_clear",
                "PERSONA_5_ROYAL": "p5_tokyo_daylight"
              },
              "tracks": [
                {"id": "p3_color_your_night", "game_theme": "PERSONA_3_RELOAD", "title": "Color Your Night",
                 "artist": "A", "time_of_day": "NIGHT", "weather": "CLEAR", "variant_group": "p3_roaming",
                 "stream_url": "https://cdn.example.com/audio/p3_color_your_night.m4a", "size_bytes": 3500000},
                {"id": "p4_roaming_clear", "game_theme": "PERSONA_4_REVIVAL", "title": "P4 Clear", "artist": "A",
                 "time_of_day": "ALL", "weather": "CLEAR", "variant_group": "p4_roaming",
                 "stream_url": "https://cdn.example.com/audio/p4_roaming_clear.m4a"},
                {"id": "p5_tokyo_daylight", "game_theme": "PERSONA_5_ROYAL", "title": "Tokyo Daylight", "artist": "A",
                 "time_of_day": "DAY", "weather": "CLEAR",
                 "stream_url": "https://cdn.example.com/audio/p5_tokyo_daylight.m4a"}
              ]
            }
            """.trimIndent(),
        )
        assertTrue(result.issues.isEmpty())
        val m = notNull(result.manifest)
        assertEquals(3, m.tracks.size)
        assertEquals(3_500_000L, m.trackById("p3_color_your_night")?.sizeBytes)
        assertEquals(GameTheme.PERSONA_5_ROYAL, m.trackById("p5_tokyo_daylight")?.gameTheme)
    }

    @Test
    fun originalV1SampleWithMarkdownUrlsIsRepairedNotCrashed() {
        val result = parse(
            """
            {"version": 1, "tracks": [
              {"id": "p3_color_your_night", "game_theme": "PERSONA_3_RELOAD", "title": "Color Your Night",
               "artist": "A", "time_of_day": "NIGHT", "weather": "CLEAR",
               "stream_url": "https://cdn.example.com/audio/p3_color_your_night.m4a"},
              {"id": "p4_heartbeat_rain", "game_theme": "PERSONA_4_REVIVAL", "title": "Heartbeat", "artist": "A",
               "time_of_day": "ALL", "weather": "RAIN",
               "stream_url": "[https://cdn.example.com/audio/p4_heartbeat_rain.m4a](https://cdn.example.com/audio/p4_heartbeat_rain.m4a)"}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("p3_color_your_night"), notNull(result.manifest).tracks.map { it.id })
        assertEquals(1, result.issues.count { it.severity == IssueSeverity.ERROR })
    }

    @Test
    fun unknownKeysNullsAndWrongShapesDoNotCrashDecoding() {
        val result = parse(
            """
            {"version": 3, "future_field": {"a": 1}, "tracks": [
              {"id": "a", "game_theme": "PERSONA_3_RELOAD", "title": "T", "stream_url": "https://x.example/a.m4a",
               "weather": "HAIL", "extra": true, "size_bytes": null, "artist": null}
            ]}
            """.trimIndent(),
        )
        val t = notNull(result.manifest).tracks.single()
        assertEquals(TrackWeather.ALL, t.weather)
        assertEquals("Unknown artist", t.artist)
    }
}
