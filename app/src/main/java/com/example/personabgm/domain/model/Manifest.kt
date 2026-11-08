package com.example.personabgm.domain.model

/** One playable track from the manifest. `id` is stable and doubles as the audio cache key. */
data class Track(
    val id: String,
    val gameTheme: GameTheme,
    val title: String,
    val artist: String,
    val timeOfDay: TrackTime,
    val weather: TrackWeather,
    /** Tracks sharing a group are variants of one slot; null means standalone (spec 5.3). */
    val variantGroup: String?,
    val streamUrl: String,
    val sizeBytes: Long?,
)

/** A validated manifest. Track order is the order in the source file (used for Next/Previous). */
data class Manifest(
    val version: Int,
    /** Roaming default track id per theme (spec 5.3 stage 1). A theme may be absent if it has no tracks. */
    val themeDefaults: Map<GameTheme, String>,
    val tracks: List<Track>,
) {
    private val index: Map<String, Track> by lazy(LazyThreadSafetyMode.NONE) { tracks.associateBy { it.id } }

    fun trackById(id: String?): Track? = if (id == null) null else index[id]

    fun themeDefault(theme: GameTheme): Track? = trackById(themeDefaults[theme])

    fun tracksFor(theme: GameTheme): List<Track> = tracks.filter { it.gameTheme == theme }

    /** All tracks in the same variant group as [track], or just [track] when it is standalone. */
    fun variantsOf(track: Track): List<Track> {
        val group = track.variantGroup ?: return listOf(track)
        return tracks.filter { it.variantGroup == group }
    }

    /** Tracks for this single-user device: zone assignments plus the active theme's default.
     * The selected theme default is last so it is recent in the LRU working set.
     */
    fun precacheTracks(
        assignedTrackIds: Collection<String> = emptyList(),
        activeTheme: GameTheme? = null,
    ): List<Track> {
        val defaultIds = if (activeTheme == null) themeDefaults.values.toList()
            else listOfNotNull(themeDefaults[activeTheme])
        val ids = if (activeTheme == null) defaultIds + assignedTrackIds
            else assignedTrackIds.distinct() + defaultIds
        return ids.mapNotNull { trackById(it) }
            .flatMap { variantsOf(it) }
            .distinctBy { it.id }
    }
}
