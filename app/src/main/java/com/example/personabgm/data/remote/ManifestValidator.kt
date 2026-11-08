package com.example.personabgm.data.remote

import com.example.personabgm.domain.model.GameTheme
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.Track
import com.example.personabgm.domain.model.TrackTime
import com.example.personabgm.domain.model.TrackWeather
import java.net.URI
import java.net.URISyntaxException

enum class IssueSeverity {
    /** Something unusual was repaired (for example an unknown tag became ALL). */
    WARNING,

    /** A track was dropped or the whole manifest was rejected. */
    ERROR,
}

data class ManifestIssue(
    val severity: IssueSeverity,
    val message: String,
    val trackId: String? = null,
)

data class ValidationResult(
    /** Null when the manifest as a whole is unusable. */
    val manifest: Manifest?,
    val issues: List<ManifestIssue>,
)

/**
 * Turns an untrusted [ManifestDto] into a trusted [Manifest] (spec 7.1 client rules):
 *  - bad tracks are dropped one by one, never failing the whole file;
 *  - stream URLs must be plain https (this is the regression guard for the v1 markdown-link bug);
 *  - duplicate ids keep the first track;
 *  - unknown time/weather tags become ALL with a warning;
 *  - every theme gets a roaming default, repaired from its own tracks when the declared one is bad.
 *
 * Pure Kotlin on purpose so it is fully unit-testable on the JVM.
 */
object ManifestValidator {

    fun validate(dto: ManifestDto, allowedStreamHost: String? = null): ValidationResult {
        val issues = ArrayList<ManifestIssue>()

        val version = dto.version
        if (version == null || version < 1) {
            issues += ManifestIssue(IssueSeverity.ERROR, "Manifest has no valid 'version'; rejected.")
            return ValidationResult(null, issues)
        }

        val seenIds = HashSet<String>()
        val tracks = ArrayList<Track>()
        dto.tracks.orEmpty().forEachIndexed { index, raw ->
            validateTrack(raw, index, seenIds, issues, allowedStreamHost)?.let { tracks += it }
        }

        if (tracks.isEmpty()) {
            issues += ManifestIssue(IssueSeverity.ERROR, "Manifest has no valid tracks; rejected.")
            return ValidationResult(null, issues)
        }

        warnOnMixedThemeVariantGroups(tracks, issues)
        val defaults = resolveThemeDefaults(dto.themeDefaults.orEmpty(), tracks, issues)
        return ValidationResult(Manifest(version, defaults, tracks), issues)
    }

    private fun validateTrack(
        raw: TrackDto,
        index: Int,
        seenIds: MutableSet<String>,
        issues: MutableList<ManifestIssue>,
        allowedStreamHost: String?,
    ): Track? {
        val id = raw.id?.trim().orEmpty()
        if (id.isEmpty()) {
            issues += ManifestIssue(IssueSeverity.ERROR, "Track #$index has no id; dropped.")
            return null
        }

        fun drop(reason: String): Track? {
            issues += ManifestIssue(IssueSeverity.ERROR, "Track '$id' dropped: $reason.", id)
            return null
        }

        fun warn(message: String) {
            issues += ManifestIssue(IssueSeverity.WARNING, "Track '$id': $message.", id)
        }

        if (id in seenIds) return drop("duplicate id (first one kept)")

        val theme = GameTheme.entries.firstOrNull { it.name == raw.gameTheme }
            ?: return drop("unknown game_theme '${raw.gameTheme}'")

        val title = raw.title?.trim().orEmpty()
        if (title.isEmpty()) return drop("missing title")

        val url = raw.streamUrl?.trim().orEmpty()
        if (!isValidHttpsUrl(url)) return drop("stream_url is not a plain https URL")
        if (allowedStreamHost != null && !isValidPrivateWorkerAudioUrl(url, allowedStreamHost)) {
            return drop("stream_url must use the configured private music Worker /v1/audio endpoint")
        }

        val timeTag = raw.timeOfDay?.trim()
        val time = TrackTime.fromNameOrAll(timeTag)
        if (!timeTag.isNullOrEmpty() && time == TrackTime.ALL && timeTag != TrackTime.ALL.name) {
            warn("unknown time_of_day '$timeTag', treated as ALL")
        }

        val weatherTag = raw.weather?.trim()
        val weather = TrackWeather.fromNameOrAll(weatherTag)
        if (!weatherTag.isNullOrEmpty() && weather == TrackWeather.ALL && weatherTag != TrackWeather.ALL.name) {
            warn("unknown weather '$weatherTag', treated as ALL")
        }

        val sizeBytes = raw.sizeBytes?.takeIf { it > 0 }
        if (raw.sizeBytes != null && sizeBytes == null) warn("ignored non-positive size_bytes")

        seenIds += id
        return Track(
            id = id,
            gameTheme = theme,
            title = title,
            artist = raw.artist?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unknown artist",
            timeOfDay = time,
            weather = weather,
            variantGroup = raw.variantGroup?.trim().takeUnless { it.isNullOrEmpty() },
            streamUrl = url,
            sizeBytes = sizeBytes,
        )
    }

    private fun warnOnMixedThemeVariantGroups(tracks: List<Track>, issues: MutableList<ManifestIssue>) {
        tracks.filter { it.variantGroup != null }
            .groupBy { it.variantGroup }
            .forEach { (group, members) ->
                if (members.map { it.gameTheme }.distinct().size > 1) {
                    issues += ManifestIssue(
                        IssueSeverity.WARNING,
                        "variant_group '$group' mixes tracks from different themes.",
                    )
                }
            }
    }

    private fun resolveThemeDefaults(
        declared: Map<String, String>,
        tracks: List<Track>,
        issues: MutableList<ManifestIssue>,
    ): Map<GameTheme, String> {
        fun warn(message: String) {
            issues += ManifestIssue(IssueSeverity.WARNING, message)
        }

        declared.keys
            .filter { key -> GameTheme.entries.none { it.name == key } }
            .forEach { warn("theme_defaults has unknown theme '$it'; ignored.") }

        val byId = tracks.associateBy { it.id }
        val result = LinkedHashMap<GameTheme, String>()

        for (theme in GameTheme.entries) {
            val declaredId = declared[theme.name]?.trim()
            val declaredTrack = declaredId?.let { byId[it] }

            if (declaredTrack != null && declaredTrack.gameTheme == theme) {
                result[theme] = declaredTrack.id
                continue
            }

            val themeTracks = tracks.filter { it.gameTheme == theme }
            if (themeTracks.isEmpty()) {
                warn("Theme $theme has no playable tracks, so it has no roaming default.")
                continue
            }

            val fallback = themeTracks.firstOrNull {
                it.timeOfDay == TrackTime.ALL && it.weather == TrackWeather.ALL
            } ?: themeTracks.first()

            val why = when {
                declaredId.isNullOrEmpty() -> "has no theme_defaults entry"
                declaredTrack == null -> "points to missing track '$declaredId'"
                else -> "points to track '$declaredId' from another theme"
            }
            warn("Theme $theme $why; using '${fallback.id}' instead.")
            result[theme] = fallback.id
        }
        return result
    }

    internal fun isValidPrivateWorkerAudioUrl(url: String, allowedHost: String): Boolean {
        val uri = try { URI(url) } catch (_: URISyntaxException) { return false }
        val port = uri.port.takeIf { it >= 0 } ?: 443
        return uri.scheme.equals("https", true) && uri.host.equals(allowedHost, true) && port == 443 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath?.startsWith("/v1/audio/") == true && uri.rawPath.length > "/v1/audio/".length
    }

    /** Plain `https://host/...` only. Rejects markdown links, whitespace, other schemes, and missing hosts. */
    internal fun isValidHttpsUrl(url: String): Boolean {
        if (url.isEmpty() || url.any { it.isWhitespace() }) return false
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }
}
