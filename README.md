# Persona BGM

Android app that plays location-, time-, and weather-aware background music with a live-switching
Persona 3 Reload / Persona 4 Revival / Persona 5 Royal look. Full spec: `docs/PERSONA_BGM_APP_SPEC_v2.md`.

## Status

| Phase | Scope | State |
| :-- | :--- | :--- |
| 0 | Scaffold: Gradle, Hilt, Room, DataStore, OkHttp/Json, Navigation | Done |
| 1 | Theme system: 3 themes, animated live switching, haptics, reduce-motion, shared components | Done |
| 2 | Manifest API + validation + offline cache, LCE screens | **Done (this drop)** |
| 3 | Audio engine: dual ExoPlayer crossfade, focus, cache, MediaSession | Next |
| 4 | Context resolver + geofencing + weather | |
| 5 | Onboarding, map, zone editor | |
| 6 | Polish and QA | |

## Open it

1. Install **Android Studio** (a version that supports AGP 9.2) with **JDK 17+**.
2. `File > Open` this folder and let Gradle sync. The wrapper points at Gradle 9.4.1.
   (The wrapper JAR and `gradlew` scripts are not included. For command-line builds run
   `gradle wrapper --gradle-version 9.4.1` once, or just build from Android Studio.)
3. Run the `app` configuration on a device or emulator (API 26+).
4. Unit tests: `./gradlew :app:testDebugUnitTest` (palette contrast, theme fallback, manifest validator, manifest JSON parsing).

## Phase 2: the track manifest

- **Where it comes from:** `BuildConfig.MANIFEST_URL` in `app/build.gradle.kts` (placeholder
  `https://cdn.example.com/manifest.json`). Point it at your own https host. Schema: spec 7.1.
- **Fetch:** Retrofit with the cached ETag sent as `If-None-Match`; a 304 keeps the cache.
- **Validation:** `ManifestValidator` drops bad tracks one by one (non-https or markdown-wrapped
  URLs, duplicate ids, unknown theme, missing title), turns unknown time/weather tags into `ALL`,
  and repairs a bad `theme_defaults` entry from that theme's own tracks. Every repair is logged
  under the tag `ManifestRepository` and returned to the caller.
- **Offline:** the validated manifest is stored in Room. A failed refresh never clears it.
- **UI:** a themed "Connecting to Metaverse..." / "Tuning TV..." screen on a first launch with no
  cache (after a 250 ms grace so warm starts never flash it), a Library screen (Loading, Error with
  retry, Content with an offline note), and a Refresh button.
- **Debug builds only:** if the very first fetch fails and nothing is cached, the bundled
  `assets/manifest_seed.json` (placeholder URLs) is loaded so you can see real content without a
  server. Release builds never do this.

## What you should see

On a debug build with the placeholder URL: a short "Connecting..." screen, then Home. Open
**Library** to see the seed tracks grouped by theme (active theme first), the "Roaming default"
badges, the day/night/weather tags, and the "Offline. Showing your saved track list." note.

A Home screen with a "Now playing" placeholder and three theme cards. Tapping a card cross-fades
colors over ~450 ms, swaps typography, shapes, heading style, and the background pattern
(aquatic waves / halftone + CRT lines / diagonal comic band), and gives a stronger haptic tick.
The choice persists across restarts. Settings has Haptics and Reduce motion toggles.

## Toolchain

| Item | Version |
| :--- | :--- |
| Android Gradle Plugin | 9.2.1 (built-in Kotlin; no `kotlin-android` plugin) |
| Gradle | 9.4.1 |
| Kotlin / KSP | 2.3.21 / 2.3.9 |
| Hilt | 2.60.1 (its Gradle plugin requires AGP 9) |
| Compose BOM | 2026.09.00 |
| Room | 2.8.4 (Room 3 migration is optional later) |
| minSdk / target / compile | 26 / 36 / 36 |

Versions live in `gradle/libs.versions.toml`.

## Decisions made without asking (spec section 12 defaults)

Equal-power crossfade, 150 m default geofence radius, Google Maps (Phase 5), personal/sideload
build, fixed 06:00-18:00 day window, service-level audio focus controller, weather variants
apply within a track's `variant_group` only.

## Verification status of this drop

- **Run here for real:** the 21 `ManifestValidatorTest` cases plus `GameThemeTest` (compiled and run on
  the JVM with the Kotlin 2.3.21 compiler against small JUnit shims). A deliberate mutation (URL
  validation removed) made 5 of them fail, so they do catch the markdown-URL regression.
- **Not run here:** `ManifestJsonParsingTest` (needs kotlinx-serialization's compiler plugin),
  `PaletteContrastTest` (needs Compose), and everything that touches Android, Hilt, Room, or
  Retrofit. Those get their first real run on your machine.

## Spec additions found while coding

- `manifest_theme_defaults` Room table, needed to cache `theme_defaults` for offline cold starts.
- **Theme-default repair:** the spec said every `theme_defaults` entry must point at a real track
  of that theme. The validator now repairs a bad or missing entry from that theme's own tracks
  (preferring an `ALL`/`ALL` track) and warns, so one typo in the manifest cannot silence a theme.
  A theme with no valid tracks simply has no roaming default.
- Persona names are used as theme labels. Replace them with original names before any public release.

## Not done yet / known gaps

- System fonts only. Distinct bundled fonts per theme are a Phase 6 polish item.
- System "Remove animations" is read once per process; the in-app toggle applies live.
- `ShimmerBox` exists but is first used in Phase 5 (map skeleton).
- Audio is not wired yet: tracks are listed but not playable until Phase 3.
- Release builds are not minified yet (keep rules arrive in Phase 6).

## If Gradle sync or the first build fails

This drop was **not built in the authoring environment** (no Android SDK or Google Maven access
there). It was checked for syntax with the Kotlin 2.3.21 compiler, XML and version-catalog
validity, and the pure-Kotlin domain layer was compiled and run. Everything touching Android,
Compose, Hilt, and Room still needs its first real build on your machine. Likely seams:

- **KSP + AGP 9 "kotlin sourceSets not allowed" error:** add
  `android.disallowKotlinSourceSets=false` to `gradle.properties`, or bump `ksp` in the catalog.
- **A version "not found":** bump that one entry in `gradle/libs.versions.toml` to the latest
  stable. Several AndroidX entries (activity, lifecycle, navigation, datastore) are deliberately
  one or two minors behind and safe to raise.
- **Android Studio too old for AGP 9.2:** update Studio, or use its AGP Upgrade Assistant.
- Paste the first error to Claude and it will be fixed in the next drop.
