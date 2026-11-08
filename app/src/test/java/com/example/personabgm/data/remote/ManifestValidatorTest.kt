package com.example.personabgm.data.remote

import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestValidatorTest {

    /** Unwraps a value that must exist (JUnit's assertNotNull returns nothing). */
    private fun <T : Any> notNull(value: T?): T = value ?: throw AssertionError("expected a non-null value")

    private val goodUrl = "https://cdn.example.com/audio/a.m4a"

    private fun track(
        id: String,
        theme: String? = "PERSONA_3_RELOAD",
        title: String? = "Title $id",
        time: String? = "ALL",
        weather: String? = "ALL",
        group: String? = null,
        url: String? = goodUrl,
        size: Long? = null,
    ) = TrackDto(
        id = id, gameTheme = theme, title = title, artist = "Artist",
        timeOfDay = time, weather = weather, variantGroup = group, streamUrl = url, sizeBytes = size,
    )

    private fun manifest(
        tracks: List<TrackDto>,
        defaults: Map<String, String>? = null,
        version: Int? = 2,
    ) = ManifestDto(version = version, themeDefaults = defaults, tracks = tracks)

    private fun ValidationResult.errors() = issues.filter { it.severity == IssueSeverity.ERROR }
    private fun ValidationResult.warnings() = issues.filter { it.severity == IssueSeverity.WARNING }

    // ---- the corrected spec 7.1 sample ----

    @Test
    fun specSampleManifestIsFullyValid() {
        val dto = manifest(
            defaults = mapOf(
                "PERSONA_3_RELOAD" to "p3_color_your_night",
                "PERSONA_4_REVIVAL" to "p4_roaming_clear",
                "PERSONA_5_ROYAL" to "p5_tokyo_daylight",
            ),
            tracks = listOf(
                track("p3_color_your_night", "PERSONA_3_RELOAD", time = "NIGHT", weather = "CLEAR", group = "p3_roaming"),
                track("p4_roaming_clear", "PERSONA_4_REVIVAL", time = "ALL", weather = "CLEAR", group = "p4_roaming"),
                track("p4_heartbeat_rain", "PERSONA_4_REVIVAL", time = "ALL", weather = "RAIN", group = "p4_roaming"),
                track("p5_tokyo_daylight", "PERSONA_5_ROYAL", time = "DAY", weather = "CLEAR", group = "p5_roaming"),
            ),
        )
        val result = ManifestValidator.validate(dto)
        assertTrue(result.issues.isEmpty())
        val m = notNull(result.manifest)
        assertEquals(4, m.tracks.size)
        assertEquals("p4_roaming_clear", m.themeDefault(GameTheme.PERSONA_4_REVIVAL)?.id)
        assertEquals(listOf("p4_roaming_clear", "p4_heartbeat_rain"), m.variantsOf(m.trackById("p4_roaming_clear")!!).map { it.id })
    }

    // ---- regression: the v1 manifest had markdown-wrapped URLs ----

    @Test
    fun markdownWrappedUrlIsRejectedAndOtherTracksSurvive() {
        val md = "[https://cdn.example.com/audio/x.m4a](https://cdn.example.com/audio/x.m4a)"
        val result = ManifestValidator.validate(
            manifest(listOf(track("good"), track("bad_markdown", url = md))),
        )
        val m = notNull(result.manifest)
        assertEquals(listOf("good"), m.tracks.map { it.id })
        assertTrue(result.errors().any { it.trackId == "bad_markdown" && "https URL" in it.message })
    }

    @Test
    fun v1ManifestWithTwoBrokenUrlsKeepsOnlyTheValidTrack() {
        val v1 = ManifestDto(
            version = 1,
            tracks = listOf(
                track("p3_color_your_night", "PERSONA_3_RELOAD", time = "NIGHT", weather = "CLEAR"),
                track("p4_heartbeat_rain", "PERSONA_4_REVIVAL", weather = "RAIN", url = "[https://a/b.m4a](https://a/b.m4a)"),
                track("p5_tokyo_daylight", "PERSONA_5_ROYAL", time = "DAY", weather = "CLEAR", url = "[https://a/c.m4a](https://a/c.m4a)"),
            ),
        )
        val result = ManifestValidator.validate(v1)
        val m = notNull(result.manifest)
        assertEquals(listOf("p3_color_your_night"), m.tracks.map { it.id })
        assertEquals(2, result.errors().size)
        // v1 has no theme_defaults: P3 is repaired from its own track, P4/P5 have no playable tracks.
        assertEquals(mapOf(GameTheme.PERSONA_3_RELOAD to "p3_color_your_night"), m.themeDefaults)
    }

    @Test
    fun onlyPlainHttpsUrlsAreAccepted() {
        val ok = listOf(
            "https://cdn.example.com/a.m4a",
            "HTTPS://cdn.example.com/a.m4a",
            "https://cdn.example.com:8443/a/b.m4a?token=abc",
        )
        val bad = listOf(
            "", "http://cdn.example.com/a.m4a", "ftp://cdn.example.com/a.m4a", "file:///sdcard/a.m4a",
            "https://", "https:///a.m4a", "cdn.example.com/a.m4a", "https://cdn.example.com/a b.m4a",
            "[https://cdn.example.com/a.m4a](https://cdn.example.com/a.m4a)",
            "https://cdn.example.com/a.m4a](https://cdn.example.com/a.m4a)",
        )
        ok.forEach { assertTrue("should accept: $it", ManifestValidator.isValidHttpsUrl(it)) }
        bad.forEach { assertFalse("should reject: $it", ManifestValidator.isValidHttpsUrl(it)) }
    }


    @Test
    fun privateModeAllowsOnlyConfiguredWorkerAudioPaths() {
        val allowedHost = "music.example.workers.dev"
        val good = "https://music.example.workers.dev/v1/audio/p3/night/song.m4a"
        val rejected = listOf(
            "https://evil.example/v1/audio/song.m4a",
            "http://music.example.workers.dev/v1/audio/song.m4a",
            "https://music.example.workers.dev/v1/manifest",
            "https://music.example.workers.dev/v1/audio/song.m4a?token=secret",
            "https://user@music.example.workers.dev/v1/audio/song.m4a",
            "https://music.example.workers.dev:8443/v1/audio/song.m4a",
        )
        assertTrue(ManifestValidator.isValidPrivateWorkerAudioUrl(good, allowedHost))
        rejected.forEach { assertFalse("should reject $it", ManifestValidator.isValidPrivateWorkerAudioUrl(it, allowedHost)) }
        val result = ManifestValidator.validate(
            manifest(listOf(track("good", url = good), track("bad", url = rejected.first()))),
            allowedStreamHost = allowedHost,
        )
        assertEquals(listOf("good"), notNull(result.manifest).tracks.map { it.id })
    }

    // ---- per-track rules ----

    @Test
    fun duplicateIdKeepsFirstAndReportsError() {
        val result = ManifestValidator.validate(
            manifest(listOf(track("dup", title = "First"), track("dup", title = "Second"))),
        )
        val m = notNull(result.manifest)
        assertEquals(1, m.tracks.size)
        assertEquals("First", m.tracks.single().title)
        assertTrue(result.errors().any { "duplicate" in it.message })
    }

    @Test
    fun invalidFirstOccurrenceDoesNotBlockValidDuplicate() {
        val result = ManifestValidator.validate(
            manifest(listOf(track("x", url = "http://insecure/a.m4a"), track("x", title = "Valid one"))),
        )
        assertEquals("Valid one", notNull(result.manifest).trackById("x")?.title)
    }

    @Test
    fun unknownThemeMissingIdAndMissingTitleAreDropped() {
        val result = ManifestValidator.validate(
            manifest(
                listOf(
                    track("ok"),
                    track("alien", theme = "PERSONA_9_FUTURE"),
                    track("  ", theme = "PERSONA_3_RELOAD"),
                    track("untitled", title = "  "),
                ),
            ),
        )
        assertEquals(listOf("ok"), notNull(result.manifest).tracks.map { it.id })
        assertEquals(3, result.errors().size)
    }

    @Test
    fun unknownTimeOrWeatherBecomesAllWithWarning() {
        val result = ManifestValidator.validate(manifest(listOf(track("t", time = "DUSK", weather = "STORM"))))
        val t = notNull(result.manifest).tracks.single()
        assertEquals(TrackTime.ALL, t.timeOfDay)
        assertEquals(TrackWeather.ALL, t.weather)
        assertEquals(2, result.warnings().count { it.trackId == "t" })
        assertTrue(result.errors().isEmpty())
    }

    @Test
    fun missingTimeOrWeatherIsAllWithoutWarning() {
        val result = ManifestValidator.validate(manifest(listOf(track("t", time = null, weather = null))))
        val t = notNull(result.manifest).tracks.single()
        assertEquals(TrackTime.ALL, t.timeOfDay)
        assertEquals(TrackWeather.ALL, t.weather)
        assertTrue(result.warnings().none { it.trackId == "t" })
    }

    @Test
    fun fieldsAreTrimmedAndBlankOptionalsNormalised() {
        val result = ManifestValidator.validate(
            manifest(listOf(track("  a  ", group = "   ", url = "  $goodUrl  ", size = 0))),
        )
        val t = notNull(result.manifest).tracks.single()
        assertEquals("a", t.id)
        assertNull(t.variantGroup)
        assertEquals(goodUrl, t.streamUrl)
        assertNull(t.sizeBytes)
    }

    @Test
    fun positiveSizeIsKept() {
        val t = notNull(ManifestValidator.validate(manifest(listOf(track("a", size = 4_000_000)))).manifest).tracks.single()
        assertEquals(4_000_000L, t.sizeBytes)
    }

    // ---- whole-manifest rules ----

    @Test
    fun missingOrInvalidVersionRejectsManifest() {
        assertNull(ManifestValidator.validate(manifest(listOf(track("a")), version = null)).manifest)
        assertNull(ManifestValidator.validate(manifest(listOf(track("a")), version = 0)).manifest)
    }

    @Test
    fun noValidTracksRejectsManifest() {
        assertNull(ManifestValidator.validate(manifest(emptyList())).manifest)
        assertNull(ManifestValidator.validate(ManifestDto(version = 2, tracks = null)).manifest)
        assertNull(ManifestValidator.validate(manifest(listOf(track("a", url = "nope")))).manifest)
    }

    @Test
    fun manifestOrderIsPreserved() {
        val ids = listOf("z", "a", "m")
        val m = notNull(ManifestValidator.validate(manifest(ids.map { track(it) })).manifest)
        assertEquals(ids, m.tracks.map { it.id })
    }

    // ---- theme defaults ----

    @Test
    fun defaultPointingToMissingTrackFallsBackToAllAllTrack() {
        val result = ManifestValidator.validate(
            manifest(
                defaults = mapOf("PERSONA_3_RELOAD" to "ghost"),
                tracks = listOf(
                    track("night_only", time = "NIGHT"),
                    track("base", time = "ALL", weather = "ALL"),
                ),
            ),
        )
        assertEquals("base", notNull(result.manifest).themeDefaults[GameTheme.PERSONA_3_RELOAD])
        assertTrue(result.warnings().any { "missing track 'ghost'" in it.message })
    }

    @Test
    fun fallbackUsesFirstTrackWhenNoAllAllTrackExists() {
        val result = ManifestValidator.validate(
            manifest(listOf(track("first", time = "DAY"), track("second", weather = "RAIN"))),
        )
        assertEquals("first", notNull(result.manifest).themeDefaults[GameTheme.PERSONA_3_RELOAD])
        assertTrue(result.warnings().any { "no theme_defaults entry" in it.message })
    }

    @Test
    fun defaultPointingAtAnotherThemesTrackIsRepaired() {
        val result = ManifestValidator.validate(
            manifest(
                defaults = mapOf("PERSONA_3_RELOAD" to "p5_track"),
                tracks = listOf(track("p3_track"), track("p5_track", theme = "PERSONA_5_ROYAL")),
            ),
        )
        val m = notNull(result.manifest)
        assertEquals("p3_track", m.themeDefaults[GameTheme.PERSONA_3_RELOAD])
        assertTrue(result.warnings().any { "another theme" in it.message })
    }

    @Test
    fun themeWithNoTracksHasNoDefaultAndWarns() {
        val result = ManifestValidator.validate(manifest(listOf(track("only_p3"))))
        val m = notNull(result.manifest)
        assertNull(m.themeDefault(GameTheme.PERSONA_4_REVIVAL))
        assertNull(m.themeDefaults[GameTheme.PERSONA_5_ROYAL])
        assertTrue(result.warnings().any { "PERSONA_4_REVIVAL" in it.message && "no playable tracks" in it.message })
    }

    @Test
    fun unknownThemeInDefaultsIsIgnoredWithWarning() {
        val result = ManifestValidator.validate(
            manifest(defaults = mapOf("PERSONA_9_FUTURE" to "a", "PERSONA_3_RELOAD" to "a"), tracks = listOf(track("a"))),
        )
        assertEquals("a", notNull(result.manifest).themeDefaults[GameTheme.PERSONA_3_RELOAD])
        assertTrue(result.warnings().any { "unknown theme 'PERSONA_9_FUTURE'" in it.message })
    }

    // ---- variant groups ----

    @Test
    fun variantGroupMixingThemesWarnsButKeepsTracks() {
        val result = ManifestValidator.validate(
            manifest(listOf(track("a", group = "g"), track("b", theme = "PERSONA_5_ROYAL", group = "g"))),
        )
        assertEquals(2, notNull(result.manifest).tracks.size)
        assertTrue(result.warnings().any { "mixes tracks" in it.message })
    }

    @Test
    fun standaloneTrackIsItsOwnVariantGroup() {
        val m = notNull(ManifestValidator.validate(manifest(listOf(track("solo"), track("other")))).manifest)
        assertEquals(listOf("solo"), m.variantsOf(m.trackById("solo")!!).map { it.id })
    }
}
