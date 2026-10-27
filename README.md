# Persona Context-Aware BGM for Android

A native Android background-music app concept inspired by the **Persona** game series. It is designed to choose music based on where you are, the time of day, local weather, and your selected game theme—then transition between tracks smoothly while you go about your day.

> **Project status:** The repository specification is ready for implementation. The features, architecture, and roadmap below describe the intended product; they should not be read as confirmation that every feature is already implemented.

## Overview

The app aims to make background music feel context-aware. Create geofenced zones for places such as home, campus, or work and assign a track to each zone. When you stay inside a zone, the app can select the appropriate track and choose a compatible weather/time variant. When you're outside all zones, it uses the selected theme's default roaming track.

Playback is designed to continue with the screen off, use Android media controls, cache audio for offline playback, and recover geofence registrations after relevant system events where Android permits.

## Planned features

- **Context-aware music:** Select tracks based on geofence zone, weather, time of day, and active theme.
- **Three visual themes:** Persona 3 Reload, Persona 4 Revival, and Persona 5 Royal-inspired color systems.
- **Smooth transitions:** Dual-player crossfades with a 2.5-second default duration and an equal-power curve by default.
- **Background playback:** Media notification, lock-screen controls, and support for Bluetooth/head-unit media keys.
- **Offline playback:** Media3 cache plus proactive pre-caching for assigned and default tracks.
- **Zone management:** Map-based zone creation, adjustable 100–500 m radius, track assignment, and enable/disable controls.
- **Weather-aware variants:** Open-Meteo weather codes are mapped to clear, rain, or snow conditions.
- **Manual override:** Next/Previous lets you choose tracks temporarily; **Resume Auto** returns to context-based selection.
- **Reliability tools:** Permission-health prompts, geofence recovery workers, GPS warnings, cache checks, and a debug panel for debug builds.
- **Personalization:** Theme, volume, crossfade settings, pre-cache preferences, haptics, and reduced-motion controls.

## Theme system

| Theme | Primary | Accent | Background | Visual direction |
|---|---|---|---|---|
| Persona 3 Reload-inspired | `#0055FF` | `#00E5FF` | `#0A0E17` | Dark aquatic gradients and sleek cutouts |
| Persona 4 Revival-inspired | `#FFD000` | `#FF6600` | `#181818` | Pop-art details, halftone dots, and CRT-inspired lines |
| Persona 5 Royal-inspired | `#D80027` | `#FFFFFF` | `#101010` | High-contrast layouts and dynamic diagonal elements |

Theme colors and visual ideas are inspiration only. A public release should use original branding and assets unless the necessary rights have been secured.

## How track selection works

Track resolution is intentionally split into two stages:

1. **Choose a base track.** If the user is inside a zone with an assigned track, that track becomes the base. Otherwise, use the active theme's default roaming track.
2. **Choose a variant.** Among tracks in the base track's `variant_group`, select the best eligible weather/time variant. An exact weather match scores higher than an exact time-of-day match. Ties are resolved deterministically; if no variant matches, the base track plays.

This design prevents a time-of-day rule from always overriding location-based music and ensures that an unavailable variant does not leave the user with silence.

### Context rules

- **Day:** 06:00–18:00 local device time.
- **Night:** 18:00–06:00 local device time.
- **Weather:** Clear, rain, or snow, based on Open-Meteo WMO codes.
- **Weather fallback:** Weather is treated as clear when the request fails or the result is more than three hours old.
- **Zone entry:** A 15-second geofence dwell delay helps avoid switching tracks immediately at a boundary.
- **Zone exit:** A 15-second grace period helps prevent rapid track changes near zone boundaries. If zones overlap, the most recently dwelled-in zone wins.
- **Manual override:** Next/Previous suppress automatic resolver changes until Resume Auto is selected or a new zone dwell occurs.

Android geofencing can be delayed by the operating system, device settings, and location availability; instant switching cannot be guaranteed.

## Technology stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.0+ (K2) |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM + Clean Architecture |
| Audio | Jetpack Media3, ExoPlayer, MediaSessionService, SimpleCache, CacheWriter |
| Location | Google Play Services Geofencing and Fused Location Provider APIs |
| Local database | Room |
| Preferences | Jetpack DataStore |
| Networking | Retrofit, OkHttp, Kotlinx Serialization |
| Weather | Open-Meteo API |
| Dependency injection | Hilt with KSP |
| Background work | WorkManager |
| Map | Maps Compose / Google Maps SDK (MapLibre + OpenStreetMap is an alternative) |

Library versions should be pinned to current stable releases in the Gradle version catalog when implementation begins. The proposed minimum Android version is **API 26**; the compile and target SDK should be set to the latest stable versions required at implementation and release time.

## Architecture

The design separates the UI, business rules, persistence, and long-running playback service.

```text
Compose UI + ViewModels
        │
        ▼
Domain models + use cases + ContextResolver
        │
        ▼
Repositories
        │
        ├── Room (zones, active-zone state, cached manifest)
        ├── DataStore (theme and user settings)
        ├── Retrofit / OkHttp (track manifest and weather)
        ├── Google Play Services (geofencing)
        └── Media3 cache (audio)

PlaybackService (MediaSessionService)
  ├── ExoPlayer A + ExoPlayer B
  ├── CrossfadeController / CrossfadePlayer
  ├── AudioFocusController
  └── ContextEngine

GeofenceReceiver ──► persist transition ──► PlaybackService
BootReceiver / WorkManager ──► geofence registration and health checks
```

### Suggested package layout

```text
com.yourapp.persona/
├── di/                 # Hilt modules
├── data/
│   ├── local/          # Room entities, DAOs, DataStore
│   ├── remote/         # Manifest/weather APIs and DTOs
│   └── repository/     # Data implementations
├── domain/
│   ├── model/          # Track, Zone, PlaybackContext, themes, weather
│   ├── resolver/       # Pure Kotlin context resolver
│   └── usecase/
├── audio/              # Playback service, crossfade, focus, cache workers
├── geofence/           # Geofence manager, receivers, health worker
├── context/            # Weather mapping and time-of-day engine
└── ui/
    ├── theme/
    ├── onboarding/
    ├── home/
    ├── map/
    ├── zones/
    ├── library/
    ├── settings/
    └── components/
```

The `com.yourapp.persona` package is a proposed placeholder and should be replaced with the actual application package name.

## Screens

- **Onboarding:** Theme selection and step-by-step permission setup.
- **Home / Now Playing:** Current track, context indicators, playback controls, permission health, and GPS status.
- **Map:** Current position and configured geofence zones.
- **Zone Editor:** Zone name, radius, assigned track, enable/disable, and delete actions.
- **Library:** Tracks grouped by theme with cache status and manual pre-cache controls.
- **Settings:** Theme, volume, crossfade behavior, weather override, Wi-Fi-only pre-caching, haptics, reduced motion, and cache management.
- **Debug panel:** Resolver output, registered zones, recent weather fetch, and cache statistics (debug builds only).

## Audio and offline behavior

The audio design uses two ExoPlayer instances to prepare the incoming track while the current one continues playing. The incoming track is prepared at zero volume, then both tracks crossfade over the configured duration. Equal-power crossfading is the default; linear crossfading remains an option.

A shared Media3 `SimpleCache` is planned with a **500 MB** limit. A `PrecacheWorker` downloads tracks assigned to zones and each theme's default group, with an optional unmetered-network-only constraint. Stable track IDs are used as cache keys so a changed streaming URL does not automatically invalidate cached audio.

If a new track is unavailable or fails to become ready within eight seconds, the app should keep the current track playing and retry once rather than switching to silence. Offline playback is available only for audio that has already been cached.

## Track manifest

The planned JSON manifest uses schema version 2. It defines a default roaming track for every theme and a list of tracks with stable IDs, theme, title, artist, weather/time conditions, optional variant group, and an HTTPS stream URL.

Example structure (the URL is illustrative):

```json
{
  "version": 2,
  "theme_defaults": {
    "PERSONA_3_RELOAD": "p3_roaming",
    "PERSONA_4_REVIVAL": "p4_roaming",
    "PERSONA_5_ROYAL": "p5_roaming"
  },
  "tracks": [
    {
      "id": "p3_roaming",
      "game_theme": "PERSONA_3_RELOAD",
      "title": "Example Track",
      "artist": "Example Artist",
      "time_of_day": "ALL",
      "weather": "ALL",
      "variant_group": "p3_roaming_group",
      "stream_url": "https://your-host.example/audio/example.m4a"
    }
  ]
}
```

Before use, replace illustrative values and URLs with real, authorized content. The client should validate HTTPS URLs, unique track IDs, and that every theme default refers to a track belonging to the corresponding theme. Invalid individual tracks should be rejected without discarding an otherwise valid manifest.

## Getting started

**Implementation note:** This README is based on the project specification. Exact Gradle tasks, application IDs, package names, and dependency versions depend on the Android project scaffold and are not specified as existing repository details here.

1. Open the Android project in the latest stable Android Studio and allow Gradle to sync.
2. Configure the required Android SDK and the project's version catalog and Gradle plugins.
3. If using Google Maps, add the Maps API key locally in `local.properties` (or configure the Secrets Gradle Plugin). Never commit API keys.

Example local configuration:

```properties
MAPS_API_KEY=your_google_maps_api_key
```

4. Configure a track-manifest host and populate it with HTTPS URLs pointing to audio you are authorized to stream.
5. Build and run the app on an Android device. Grant foreground location first, then follow the separate background-location step if geofence automation is needed.
6. For realistic geofence validation, test on physical devices and while walking/driving; emulator tests alone cannot establish real-world trigger reliability.

## Permissions and platform behavior

The planned permission set includes:

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` — foreground location and geofence setup.
- `ACCESS_BACKGROUND_LOCATION` — geofencing when the app is not in use; requested in a separate onboarding step on supported Android versions.
- `POST_NOTIFICATIONS` — playback notification visibility on Android 13+.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — media playback service.
- `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` — streaming, connectivity checks, and playback support.
- `RECEIVE_BOOT_COMPLETED` — re-register geofences after reboot or app replacement.
- `BLUETOOTH_CONNECT` — optional display of the connected output device on Android 12+.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — optional exemption flow, subject to platform and Google Play policy.

The app should explain why permissions are needed, re-check them when the app resumes, and provide shortcuts to the relevant system settings. If background location is denied, zone automation may be limited to times when the app is in use, while manual playback can remain available. Android and OEM battery management can still affect reliability.

The baseline design does **not** require a location foreground service for continuous tracking; geofencing is handled through Google Play Services. A location foreground-service type should only be added if continuous location updates are later implemented in a service.

## Privacy

- Geofence zone coordinates are stored locally in Room.
- Coordinates sent to Open-Meteo are rounded to two decimal places.
- The specification does not include server-side accounts or cloud sync.
- Avoid adding analytics or third-party SDKs that access location without a clear need and appropriate disclosure.
- If distributed through Google Play, complete the required background-location disclosures, core-functionality justification, and Data Safety declarations.

## Testing plan

### Unit tests

- Context resolution: zones, theme defaults, weather/time variants, tie-breaking, and missing variants.
- Crossfade gain curves and endpoints.
- Weather code mapping and unknown values.
- Day/night boundaries, midnight rollover, and timezone changes.
- Manifest parsing and validation, including invalid URLs, duplicate IDs, and unknown enum values.
- Context state machine: overlapping zones, exit grace, and manual override.

### Integration and device tests

- Room DAOs and migrations.
- Pre-caching, download failures, and cache-health recovery.
- Reboot recovery and geofence re-registration.
- Background playback, focus loss/return, headphone disconnects, and Bluetooth/car media controls.
- Offline cold start with a cached manifest and cached audio.
- Track loop points and multi-hour battery use.
- Manufacturer-specific background-process restrictions on representative devices.

## Roadmap

- [ ] **Phase 0 — Scaffold:** Android project, dependency injection, Room, DataStore, networking, navigation, and CI checks.
- [ ] **Phase 1 — Theme system:** Three live-switching themes, haptics, and reduced-motion support.
- [ ] **Phase 2 — Data layer:** Manifest validation, offline manifest storage, zone persistence, and UI loading/error states.
- [ ] **Phase 3 — Audio engine:** Playback service, dual-player crossfade, audio focus, media controls, and pre-caching.
- [ ] **Phase 4 — Context and geofencing:** Resolver, weather, day/night clock, zone transitions, and recovery workers.
- [ ] **Phase 5 — Onboarding and map UX:** Permission flow, zone editor, GPS warnings, and battery/OEM guidance.
- [ ] **Phase 6 — Polish and QA:** Debug tools, accessibility, battery profiling, and the complete device-test matrix.

## Licensing and attribution

**Persona** and related game names, soundtracks, logos, fonts, and artwork are associated with their respective rights holders, including Atlus/Sega. This project is a fan-inspired technical concept and is not affiliated with or endorsed by those companies. Do not bundle or redistribute copyrighted soundtrack files, logos, or artwork without permission. Use original audio or content for which you have the appropriate rights, and check the current terms for any third-party API before distribution.

The Open-Meteo API is planned for weather data; its current terms should be reviewed before commercial use. Check Google Play policies and applicable platform requirements before publishing.

## Contributing

Contributions should follow the planned separation between UI, domain logic, data sources, and service-level playback. Add unit tests for resolver and state-machine changes, avoid committing credentials or copyrighted media, and document any changes to the manifest schema or permission behavior.

## Disclaimer

This README describes the intended design in `PERSONA_BGM_APP_SPEC_v2.md`. Verify implementation status, dependency versions, build configuration, legal requirements, and API terms against the actual codebase and current platform documentation before release.
