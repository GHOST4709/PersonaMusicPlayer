# Persona BGM

Android app that plays location-, time-, and weather-aware background music with a live-switching
Persona 3 Reload / Persona 4 Revival / Persona 5 Royal look. Full spec: `docs/PERSONA_BGM_APP_SPEC_v2.md`.

## Status

| Phase | Scope | State |
| :-- | :--- | :--- |
| 0 | Scaffold: Gradle, Hilt, Room, DataStore, OkHttp/Json, Navigation | Done |
| 1 | Theme system: 3 themes, animated live switching, haptics, reduce-motion, shared components | Done |
| 2 | Manifest API + validation + offline cache, LCE screens | Done |
| 3 | Audio engine: dual ExoPlayer crossfade, focus, cache, MediaSession | Done |
| 4 | Context resolver + geofencing + weather + time of day | Done |
| 5 | Onboarding, map, zone editor, permission health, GPS banner | Done |
| 6 | Polish and QA: fonts, accessibility, minification, debug panel, fast zone detection, test plan | **Done (this drop)**: needs your device testing |

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

## Phase 3: the audio engine

What exists now: pressing **Play** starts the active theme's default track; switching theme while it plays
crossfades to the new theme's default; tapping a track in the Library crossfades to it (Manual Override);
Next/Previous and "Resume auto" work from the app, the notification, and Bluetooth/car keys.

| Piece | File | Notes |
| :--- | :--- | :--- |
| Two ExoPlayers, fades, master gain | `audio/CrossfadeEngine.kt` | Prepare incoming silently, wait for READY (8 s timeout, one retry), fade, swap, rebind session once |
| Session facade | `audio/CrossfadePlayer.kt` | `ForwardingPlayer` routing play/pause/next/previous to the engine |
| Audio focus (single owner) | `audio/AudioFocusController.kt` + `FocusPolicy.kt` | Both players run with built-in focus OFF. Duck to 20%, transient pause/resume, permanent loss = stay paused |
| Fade math, Next/Previous rules | `audio/FadeMath.kt`, `OverrideNavigator.kt` | Pure Kotlin, unit-tested |
| Cache | `audio/MediaCache.kt` | One 500 MB LRU `SimpleCache` in `filesDir`, keyed by track id |
| Pre-cache | `audio/PrecacheWorker.kt` | WorkManager, Wi-Fi-only by default, resumes partial downloads, gives up after 5 attempts |
| Service | `audio/PlaybackService.kt` | `mediaPlayback` foreground service; keeps playing when the app is swiped away, stops if idle |
| UI bridge | `audio/PlaybackRemote.kt` | Command queue + state flow (UI and service share a process, so no bound controller) |

In Phase 3, AUTO means "the active theme's default track". The weather, time-of-day, and zone logic that
replaces `CrossfadeEngine.autoTarget()` is Phase 4.

### You must supply audio to hear anything

The sample manifest points at `cdn.example.com`, which does not exist, so playing a seed track ends with
"Couldn't play this track". To test, host a few audio files you own (m4a or mp3) at **https** URLs
(GitHub Pages, S3, R2, any static host) and either:

1. edit the `stream_url` values in `app/src/main/assets/manifest_seed.json` (debug builds use it when the
   first fetch fails), or
2. host a full manifest and set `MANIFEST_URL` in `app/build.gradle.kts`.

Plain `http://` URLs are rejected on purpose (spec 7.1).

### Device test checklist for Phase 3

1. Play a Library track: audio starts, notification appears (grant the notification prompt on Android 13+).
2. Switch theme while playing in Auto: a 2.5 s crossfade to the new theme's default. No silence, no dip.
3. Tap another track: crossfade, "Manual pick" shown, **Resume auto** returns to the theme's track.
4. Pause then Play: a short fade-in, no pop (check on Bluetooth).
5. Unplug headphones or disconnect Bluetooth: pauses at once.
6. A notification sound from another app: ducks to about 20%, then restores. A phone call: pauses, resumes after.
7. Start another music app: this one pauses and does **not** auto-resume.
8. Let tracks download (Library shows "Saved"), enable airplane mode, restart the app: still plays.
9. Bluetooth or car Next/Previous keys: enters Manual Override.
10. Swipe the app away while playing: keeps playing. While paused: the service stops.
11. Point a manifest track at a dead URL and crossfade to it: stays on the current track, shows a message, retries once.
12. Listen to the loop point of each file for a gap. If you hear one, encode the loops as Opus/OGG.

## Phase 4: context (zones, weather, time of day)

AUTO now plays what the context says instead of "the theme's default". Priority is the two-stage rule of
spec 5.3: first the **base track** (the active zone's assigned track, else the theme's roaming default),
then the best **variant** of that track's `variant_group` for the current weather (+2) and time of day (+1).
If no variant fits, the base track plays; a zone is never silent.

| Piece | File | Notes |
| :--- | :--- | :--- |
| Resolver | `domain/resolver/ContextResolver.kt` | Pure function, 17 tests including the case that broke the v1 cascade |
| Active zone | `context/ZoneTracker.kt` | Most recent DWELL wins, 15 s exit grace cancelled by re-entry, falls back to an overlapping zone |
| Wiring | `context/ContextEngine.kt` | Combines settings, manifest, zones, zone state, weather, and time into `AutoContext`; 1 s debounce after the first value |
| Weather | `context/Weather.kt`, `data/repository/WeatherRepository.kt` | Open-Meteo, 20 min cadence, refetch after moving 5 km, CLEAR after 3 h, coordinates rounded to 2 decimals |
| Time of day | `context/TimeBuckets.kt` | Day 06:00 to 18:00; DST-safe; re-evaluated on clock, date, and time zone changes |
| Geofences | `geofence/GeofenceManager.kt` | DWELL with 15,000 ms loitering delay + EXIT; idempotent `sync()`; mutable PendingIntent (Android 12+) |
| Receivers | `geofence/GeofenceReceiver.kt`, `BootReceiver.kt` | Persist the transition first; boot only re-registers (Android 15 forbids starting media services from boot) |
| Safety net | `geofence/GeofenceHealthWorker.kt` | Re-registers about every 12 h; also on every app start |
| Zones | `data/repository/ZoneRepository.kt`, `ui/zones/` | Add-here, radius 100 to 500 m (default 150), per-zone music, enable, delete; 100-zone limit |

Manual Override still wins over AUTO, except that entering a **new zone** ends it (spec 4.5).

If the OS kills the service while you are listening, the next geofence transition starts it again and music
resumes (the service posts a placeholder notification immediately so Android's start-in-foreground deadline
is met, and falls back to a tap-to-resume notification if Android refuses the start).

### Setting up a zone

Run the first-launch setup (Phase 5) so the permissions are in place, then open **Zones** and either press and
hold the map, or stand where you want one and tap **Add zone here**. **Change music** gives it its own track
(it downloads ahead of time). Press Play on Home.

### Device test checklist for Phase 4

1. Add a zone, give it a different track from your theme's default, press Play: you hear the theme default.
2. Walk out of the zone (more than the radius plus about 100 m) and back in. After the 15 s dwell the music
   crossfades to the zone's track; Home shows the zone name and the Zones screen shows **Inside**.
   Geofence events can lag by a minute or more; that is the platform, not a bug.
3. Leave again: about 15 s later it crossfades back to the theme default.
4. Tap a Library track (Manual pick), then enter a zone: Manual Override ends and the zone's track plays.
5. Move the device clock across 18:00: the Day/Night chip flips; a track group with day and night variants switches.
6. Weather: use **Settings > About > Debug panel** (debug builds) to pretend it is raining or snowing, and
   confirm a rain- or snow-tagged variant of the playing slot is chosen. Choose **Real** to go back.
7. Reboot with a zone registered: open the app; Zones should say it is watching. No music starts by itself
   after boot (by design).
8. While playing, run `adb shell am kill com.example.personabgm` (kills the background process), then cross a
   zone boundary: music should come back. This exercises the foreground-start path.
9. Two overlapping zones: the one you dwelled in most recently wins; leaving it falls back to the other.
10. Airplane mode: zones keep working, weather counts as clear.

## Phase 5: onboarding, map, and keeping it reliable

| Piece | File | Notes |
| :--- | :--- | :--- |
| Setup flow | `ui/onboarding/OnboardingScreen.kt`, `domain/permissions/OnboardingPlan.kt` | Welcome + theme, Location, Background location (its own step), Notifications, Battery, Bluetooth. Steps adapt to the Android version; every step can be skipped |
| Permission health | `domain/permissions/PermissionHealth.kt`, `ui/permissions/PermissionHealthCard.kt` | Re-checked on every resume. Shows the most important problem with a one-tap fix; location problems cannot be hidden, others can ("Not now") until next launch |
| GPS banner | `ui/permissions/PermissionHealthCard.kt` (`GpsBanner`) | High-contrast bar when the device's Location Services are off; follows the toggle live |
| One place for all asks | `ui/permissions/PermissionActions.kt` | Runtime requests plus every system screen (battery, app settings, auto-revoke, notification settings), with fallbacks |
| Map | `ui/zones/ZoneMap.kt`, `ui/zones/ZonesScreen.kt` | Press and hold to drop a zone (haptic), tap to select, radius circles in theme colours, theme-coloured skeleton until the map renders |
| Zone editor | `ui/zones/ZonesScreen.kt` | Rename, radius 100 to 500 m, per-zone music, enable, delete (with confirmation) |
| Battery / OEM help | `domain/permissions/OemGuidance.kt`, Settings > Reliability | Samsung, Xiaomi, OnePlus, Oppo/Realme/Vivo, Huawei/Honor advice with links; "Keep permissions" shortcut for Android's auto-revoke |

First launch opens setup; afterwards it opens Home. **Settings > Reliability > Run setup again** repeats it.

### Turning the map on

The map needs a Google Maps API key. Without one the Zones screen shows a note and everything else still works.

1. In Google Cloud, enable **Maps SDK for Android** and create an API key.
2. Restrict the key to this app: Android apps, package `com.example.personabgm` (or yours) plus your debug/release
   SHA-1 fingerprints. An unrestricted key can be abused if it leaks.
3. Add `MAPS_API_KEY=your-key` to `local.properties` (it is git-ignored) and rebuild.

### Device test checklist for Phase 5

1. Fresh install: setup opens. Pick a theme; the app restyles immediately.
2. Location step: deny once, then allow with **Approximate** only. The step warns that precise location is needed.
   Allow **Precise**: it shows Allowed.
3. Background step: on Android 11+ the button opens the app's location settings page (not a dialog). Choose
   "Allow all the time", return: it shows Allowed. On Android 11+ also open "Keep permissions".
4. Skip a step, finish setup: Home shows a health card for what you skipped, with a working fix button.
5. Revoke location in system settings while the app is open, come back: the health card appears. Turn the
   device's Location Services off: the red banner appears and "Turn on" opens the right settings screen.
6. Battery step on a Samsung or Xiaomi phone shows the brand advice and link.
7. Zones: press and hold the map to drop a zone (you feel a haptic), rename it, change its radius (the circle
   updates), assign music, delete it (confirmation appears).
8. With no network or no map key, the Zones screen still works from the list.
9. Settings > Reliability > Run setup again opens setup; finishing returns to Settings.

## Phase 6: polish and QA

| Piece | Where | Notes |
| :--- | :--- | :--- |
| Fast zone detection | `geofence/ForegroundProximityMonitor.kt`, `context/ProximityTracker.kt` | While the app is on screen, uses the device's own location to notice arriving and leaving in seconds. Conservative (needs the whole accuracy circle inside or outside), same 15 s dwell as the real geofence, only runs in the foreground. Switch in Settings > Zones |
| Bundled fonts | `res/font/`, `ui/theme/PersonaStyle.kt` | Rajdhani (P3), Lilita One (P4), Anton (P5) for headings, titles, and labels; body text stays on the system font. All SIL OFL; licenses in `assets/licenses/` and `docs/licenses/` |
| Accessibility | `ui/components/Semantics.kt` and the screens | Headings are real headings, status changes are announced, decorative pieces are hidden, the map is described and its zones are also a list |
| Release build | `app/proguard-rules.pro`, `app/build.gradle.kts` | Minification and resource shrinking on; keep rules for serialization and the Retrofit interfaces; new **`releaseCheck`** build type (minified, debug-signed) so you can test the keep rules |
| Debug panel | `ui/debug/` (debug builds only) | Live view of playback, context, zones and their state, geofence status, weather, cache, manifest, permissions; **pretend the weather or time**; re-register geofences; **Copy report** for bug reports |
| Test plan | `docs/TEST_PLAN.md` | The full device test matrix (about 100 cases) with a result column |
| Spec differences | `docs/CHANGES_FROM_SPEC.md` | Every place the build interprets, extends, or departs from the spec |

### What Phase 6 could not do

Battery and performance **numbers**, a listening check of the loop points, TalkBack and large-font behavior, and a
first run of the minified build all need a real phone. They are written up as procedures in `docs/TEST_PLAN.md`
(sections 8 to 10), with suggested targets to adjust after you have measured.

### Testing the minified build

`./gradlew :app:installReleaseCheck`, then run the checklists again. Because minification changes behavior only
at runtime, the cases that matter most are in section 10 of the test plan (manifest decoding, workers, geofence
receivers, weather decoding). Keep each release's `mapping.txt` to read crash traces.

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

- **Run here for real (158 tests, all passing):** `ManifestValidatorTest` (21), `ContextResolverTest` (17),
  `ProximityTrackerTest` (17), `WeatherTest` (15), `ZoneTrackerTest` (14), `FocusPolicyTest` (11),
  `OverrideNavigatorTest` (10), `FadeMathTest` (9), `OnboardingPlanTest` (9), `PermissionHealthTest` (9),
  `TimeBucketsTest` (6, including both DST changeovers), `ZoneRulesTest` (5), `OemGuidanceTest` (4),
  `ManifestPrecacheTest` (3), `DebugReportTest` (3), `TimeFormatTest` (2), plus `GameThemeTest` and
  `ByteFormatTest`. They were compiled and run on the
  JVM with the Kotlin 2.3.21 compiler against small JUnit shims. Mutation checks confirmed they fail when
  the logic is broken (URL validation removed, equal-power fade replaced by linear, permanent focus loss
  keeping its pending resume, weather and time points swapped, re-entry no longer cancelling a pending
  exit, the 3-hour staleness cutoff off by one, the background-location step shown on every Android version,
  background location requestable before foreground, background issue ranked above missing foreground, the
  fast path ignoring accuracy, re-dwelling zones already stored as inside, and never cancelling a pending exit).
- **Not run here:** `ManifestJsonParsingTest` (needs kotlinx-serialization's compiler plugin),
  `PaletteContrastTest` (needs Compose), and **everything that touches Android**: the engine, service,
  cache, workers, receivers, geofence registration, the context engine's flow wiring, the permission and
  settings intents, the Google Maps screen, Hilt wiring, Room, Retrofit, and all Compose screens. The crossfade orchestration (timing, player state, session rebind)
  and the flow plumbing in `ContextEngine` have no automated test; the device checklists are how they get
  verified.
- The source was also checked with the Kotlin compiler for syntax errors (none), every
  project-internal import was checked to resolve to a real declaration, and a scan checked that every
  Compose extension function and delegate used in the UI is imported. That scan found and fixed one bad
  import (`matchParentSize` is a `BoxScope` member, not a top-level function) before it reached you.

## Phase 5 risks to watch on the first real run

- **Maps Compose version.** Pinned to 6.9.0 (8.x exists) because the zone map was written against the 6.x API;
  it avoids the marker-state helpers whose names changed between versions. If you bump it, expect to touch
  `ZoneMap.kt` only.
- **Map key.** A missing key hides the map by design. A wrong or unrestricted key shows a blank grey map or
  leaks quota; check logcat for "Authorization failure".
- **Battery exemption on Google Play.** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted for apps
  published there (see spec 5.7). Fine for personal builds.
- **Android 11+ background location** cannot be granted from a dialog. The app opens the right settings page,
  but it cannot see the choice until you return; it re-checks on resume.
- **Notifications and Bluetooth** are optional by design: playback and pause-on-disconnect work without them.

## Phase 4 risks to watch on the first real run

- **Starting the service from a geofence event.** Android lists geofence transitions as an exemption for
  starting a foreground service from the background. This is the documented design but I could not test
  it. If it is refused you get the tap-to-resume notification instead; tell me and I will adjust.
- **Geofence reliability.** Latency of a minute or more is normal; OEM battery managers can still stop
  delivery. The health worker and the on-start re-registration are the mitigation; Phase 5 adds the
  battery-optimization onboarding step that matters most here.
- **Weather terms.** Open-Meteo's free API is for non-commercial use; check its terms before any release.
- **Wall-clock timestamps.** Zone state uses epoch milliseconds, so a large manual clock change can skew
  the 15 s exit grace for that one transition.
- `ENTER` events are not requested (nothing uses them; every event wakes the app). The spec's code sample
  listed them as an optional UI hint.

## Design notes for Phase 3

- UI and service communicate through a singleton `PlaybackRemote` rather than a bound `MediaController`:
  one process, far fewer async moving parts. The MediaSession still serves all external controllers.
- `DefaultHttpDataSource` is used for audio instead of the OkHttp data source, avoiding a
  Media3/OkHttp version pairing risk. Easy to swap later.
- The pre-cache worker gets its dependencies through a Hilt entry point instead of `hilt-work`, so no
  custom WorkManager configuration is needed.

## Spec additions found while coding

- `manifest_theme_defaults` Room table, needed to cache `theme_defaults` for offline cold starts.
- **Theme-default repair:** the spec said every `theme_defaults` entry must point at a real track
  of that theme. The validator now repairs a bad or missing entry from that theme's own tracks
  (preferring an `ALL`/`ALL` track) and warns, so one typo in the manifest cannot silence a theme.
  A theme with no valid tracks simply has no roaming default.
- Persona names are used as theme labels. Replace them with original names before any public release.

## Phase 3 risks to watch on the first real run

- **Media3 API surface.** `ForwardingPlayer` command overrides, `MediaSession.setPlayer` after each
  crossfade, `DefaultMediaNotificationProvider.setSmallIcon`, and the `@OptIn(UnstableApi)` usage are the
  most likely places for a compile error. If the notification flickers or session state glitches after a
  crossfade, replace `CrossfadePlayer` with a `SimpleBasePlayer` (spec 4.1 names this fallback).
- **Foreground start.** The UI starts the service with `startService` from the foreground and Media3
  promotes it when playback begins. Starts from the background (geofence events) are Phase 4.
- **Clearing the audio cache while a track plays** can briefly interrupt that track; the engine's error
  retry recovers it.

## Not done yet / known gaps

- Localization: UI strings are in code, English only.
- System "Remove animations" is read once per process; the in-app toggle applies live.
- `ShimmerBox` exists but is first used in Phase 5 (map skeleton).
- Map style is one muted dark style for all three themes (the zone circles carry the theme colour).
- Release builds are minified, but the keep rules have never run: test the `releaseCheck` build first.
- Battery/performance numbers and baseline profiles are not measured; see `docs/TEST_PLAN.md` section 9.

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
