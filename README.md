# Persona BGM

Android app that plays location-, time-, and weather-aware background music with a live-switching
Persona 3 Reload / Persona 4 Revival / Persona 5 Royal look. Full spec: `docs/PERSONA_BGM_APP_SPEC_v2.md`.

## Status

| Phase | Scope | State |
| :-- | :--- | :--- |
| 0 | Scaffold: Gradle, Hilt, Room, DataStore, OkHttp/Json, Navigation | **Done (this drop)** |
| 1 | Theme system: 3 themes, animated live switching, haptics, reduce-motion, shared components | **Done (this drop)** |
| 2 | Manifest API + validation + offline cache, LCE screens | Next |
| 3 | Audio engine: dual ExoPlayer crossfade, focus, cache, MediaSession | |
| 4 | Context resolver + geofencing + weather | |
| 5 | Onboarding, map, zone editor | |
| 6 | Polish and QA | |

## Open it

1. Install **Android Studio** (a version that supports AGP 9.2) with **JDK 17+**.
2. `File > Open` this folder and let Gradle sync. The wrapper points at Gradle 9.4.1.
   (The wrapper JAR and `gradlew` scripts are not included. For command-line builds run
   `gradle wrapper --gradle-version 9.4.1` once, or just build from Android Studio.)
3. Run the `app` configuration on a device or emulator (API 26+).
4. Unit tests: `./gradlew :app:testDebugUnitTest` (palette contrast, theme fallback).

## What you should see

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

## Spec additions found while coding

- `manifest_theme_defaults` Room table, needed to cache `theme_defaults` for offline cold starts.
- Persona names are used as theme labels. Replace them with original names before any public release.

## Not done yet / known gaps

- System fonts only. Distinct bundled fonts per theme are a Phase 6 polish item.
- System "Remove animations" is read once per process; the in-app toggle applies live.
- `ConnectingScreen` and `ShimmerBox` exist but are first used in Phase 2 and Phase 5.
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
