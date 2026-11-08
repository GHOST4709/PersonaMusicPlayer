# Where the build differs from, or goes beyond, `PERSONA_BGM_APP_SPEC_v2.md`

Everything in the spec was built. This lists the places where a decision was made, an interpretation was needed, or something was added.

## Decided from the spec's open-decisions table (section 12), defaults taken

| Question | Choice |
| :--- | :--- |
| Weather outside a zone's variant group | Weather applies **within the group only** |
| Crossfade curve | Equal-power by default; linear is a setting |
| Default geofence radius | 150 m (100 m minimum), not v1's 75 m |
| Map provider | Google Maps (Maps Compose 6.9.0) |
| Distribution | Personal / sideload; Play Store policy notes are in the README |
| Time-of-day source | Fixed 06:00 to 18:00 |
| Audio focus | Service-level controller; both players run with built-in focus off |

## Interpretations

- **"Next" key** (spec 4.5): enters Manual Override, walks the active theme's tracks in manifest order; Previous walks back through a history. Resume Auto, or entering a new zone, ends it.
- **Zone exit**: the 15 s exit grace is applied by the app (`ZoneTracker`), mirroring the platform's 15 s dwell on entry, so walking a boundary does not flap the music.
- **Weather without a variant**: a rain track only plays if the zone's variant group has a rain variant; otherwise the base track plays (never silence).
- **A bad `theme_defaults` entry** is repaired from that theme's own tracks (preferring an ALL/ALL track) with a warning, instead of silencing the theme.
- **Playback intent** is persisted (a single boolean) so a geofence event can bring the service back after the OS kills it, but only if you were listening.

## Added beyond the spec

- Offline manifest cache with stale-while-revalidate, debug seed manifest, and validation that drops bad tracks one by one.
- Foreground **fast path** for zone detection (spec 5.2 marks it optional) and its Settings switch.
- Permission Health card, GPS banner, "Run setup again", OEM battery guidance, auto-revoke shortcut.
- Debug panel (debug builds only) with weather/time overrides and a copyable report.
- Bundled fonts (headings only), accessibility semantics, R8 keep rules, a `releaseCheck` build type.
- `docs/TEST_PLAN.md` and this file.

## Deliberately different from spec text

| Spec | Built | Why |
| :--- | :--- | :--- |
| ENTER transition requested "for UI hints" | Not requested | Nothing uses it and every event wakes the app |
| UI talks to the service through a bound `MediaController` | A singleton `PlaybackRemote` | One process; far fewer async moving parts. The `MediaSession` still serves notification, Bluetooth, and car |
| OkHttp data source for audio | `DefaultHttpDataSource` | Avoids a Media3/OkHttp version-pairing risk; easy to swap |
| Hilt-WorkManager for workers | Hilt entry points | No custom WorkManager configuration needed |
| `manifest_tracks` and `manifest_meta` tables | Plus `manifest_theme_defaults` | Needed to cache `theme_defaults` for offline cold starts |

## Not done

- **Localization:** UI strings are in code, English only.
- **Crash reporting and analytics:** none, by design (privacy). Debug builds have the copyable report instead.
- **Android Auto browsing**, **home-screen widget**, **MapLibre option**: out of scope for v1.
- **Baseline profiles** and measured battery/performance numbers: need a device; the plan is in `docs/TEST_PLAN.md` section 9.
- **Pinned cache** separate from the LRU cache: a single 500 MB LRU cache with a health check that re-queues missing tracks was judged enough (a track is a few MB).
- Real-device verification of everything that touches Android. See the README's "Verification status".
