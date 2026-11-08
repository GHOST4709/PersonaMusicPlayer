# Persona BGM: device test plan

This is the manual test matrix from spec section 9. The unit tests (158 of them) cover the decision logic; **everything below needs a real phone**, because it depends on Android, the OS battery manager, Google Play services, the radio, and your ears.

How to use it: copy this file, fill the **Result** column (`P` pass, `F` fail, `-` skipped) per device, and paste failures into a report using **Settings > About > Debug panel > Copy report** (debug builds).

## 0. Devices and builds

Cover at least: a Pixel or other near-stock phone, a Samsung, and one Xiaomi/Redmi/Oppo/Vivo (their battery managers differ most). Cover Android 10, 12 or 13, and 14 or newer if you can; the permission flows differ between them.

| Build | Command | Use |
| :--- | :--- | :--- |
| debug | `./gradlew :app:installDebug` | Everything, including the debug panel and the seed manifest |
| releaseCheck | `./gradlew :app:installReleaseCheck` | **Minified like release** (debug-signed). Run sections 1 to 9 again on this build; a missing R8 keep rule shows up as a crash in a code path |

Before you start: deploy the private Cloudflare Worker/R2 setup in `cloudflare/worker/README.md`, upload audio files you have rights to use, configure `MUSIC_API_BASE_URL` in `local.properties`, and enter the Worker token in Settings → Private music library.

## 1. First run and setup

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| OB-1 | Fresh install, open the app | A short "Connecting..." screen, then **Set up** (step 1 of N) | |
| OB-2 | On step 1 tap each theme | Colors, type, and background change at once; the choice survives closing the app | |
| OB-3 | Location step: allow **Approximate** only | Step warns that precise location is needed and still shows "Not yet" | |
| OB-4 | Tap Allow location again, choose **Precise** | Step shows **Allowed**; button changes to Next | |
| OB-5 | Background step before granting foreground location (go Back, revoke in system settings, return) | Shows "Allow your location in the step before this one first." and no request button | |
| OB-6 | Background step on Android 11+ | Button opens the app's location permission page (no dialog); choose "Allow all the time", return: **Allowed** | |
| OB-7 | Android 10 only | Background step shows a system dialog with "Allow all the time" | |
| OB-8 | Android 11+: tap "Open that setting" | Opens "Pause app activity if unused" (or app settings if absent) | |
| OB-9 | Android 13+: Notifications step | System prompt; after allowing, shows Allowed | |
| OB-10 | Battery step | System dialog to allow background activity; on Samsung/Xiaomi/Oppo/Vivo/Huawei the brand advice and link appear | |
| OB-11 | Bluetooth step (Android 12+) | Marked optional; skipping is fine | |
| OB-12 | Skip every step, finish | Lands on Home; a Permission Health card appears for what is missing | |
| OB-13 | Kill and reopen after finishing | Opens Home, not setup | |
| OB-14 | Settings > Reliability > **Run setup again**, finish | Returns to Settings | |

## 2. Permission health, GPS banner, reliability settings

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| PH-1 | Home with background location missing | Card "Allow location all the time"; the fix button opens the right settings page; **App settings** is offered; no "Not now" | |
| PH-2 | Fix it, return to the app | Card disappears without restarting | |
| PH-3 | With only notifications or battery missing | Card offers **Not now**; dismissing hides it until the next launch | |
| PH-4 | Several problems at once | Most important first (location, then notifications, then battery); "N more to check" line | |
| PH-5 | Turn the device's Location Services off while the app is open | Red banner appears within a few seconds; **Turn on** opens the location settings screen | |
| PH-6 | Turn it back on | Banner disappears | |
| PH-7 | Revoke location in system settings while the app is open, return | Health card appears | |
| PH-8 | Settings > Reliability | Shows Allowed/Restricted correctly; the buttons open battery settings and "Keep permissions" | |

## 3. Themes and interface

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| TH-1 | Switch themes on Home | 450 ms color cross-fade; the heading style changes (P3 rule, P4 tilted badge, P5 slanted slab); background pattern changes | |
| TH-2 | Look at headings and buttons | P3 uses Rajdhani, P4 Lilita One, P5 Anton; body text is the system font in all three | |
| TH-3 | Settings > Haptic feedback off | No vibration on theme switch or taps; on: a tick on navigation, a stronger one on theme switch | |
| TH-4 | Settings > Reduce motion on | Waves/halftone stop animating, screen transitions disappear, colors snap | |
| TH-5 | System "Remove animations" on, restart the app | Same as TH-4 even with the in-app toggle off | |
| TH-6 | Rotate the phone on every screen | Nothing is lost or overlaps; playback continues | |

## 4. Manifest, library, offline

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| MF-1 | First launch online with your manifest URL | Library lists tracks grouped by theme, active theme first, with "Roaming default" badges | |
| MF-2 | Host a manifest with one bad `stream_url` (plain `http://` or a markdown link) | That track is missing, the rest load; logcat tag `ManifestRepository` says why | |
| MF-3 | Airplane mode, cold start | Library still opens from the saved copy and says "Offline. Showing your saved track list." | |
| MF-4 | First launch offline on a **release** build | Error card "Couldn't load the track list" with **Try again** | |
| MF-5 | First launch offline on a **debug** build | Seed tracks appear (placeholder URLs) | |
| MF-6 | Library > Refresh | Button shows "Refreshing..." then updates; an unchanged manifest costs a 304 (check with a proxy if curious) | |
| MF-7 | Wait for downloads on Wi-Fi | Tracks gain a **Saved** chip; Settings shows cache size | |
| MF-8 | Clear audio cache while idle, then while a track is playing | Idle: saved spans clear; playback: loaded/incoming tracks remain until released, then are removed | |
| MF-9 | Turn "Download on Wi-Fi only" off, use mobile data | Downloads start on mobile data | |

## 5. Playback and audio

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| AU-1 | Press Play on Home | The theme's default track starts with a short fade-in; notification with controls appears | |
| AU-2 | Switch theme while playing | A 2.5 s crossfade to the new theme's default: no gap, no dip, no double-play | |
| AU-3 | Tap another track in the Library | Crossfade; Home says "Manual pick"; **Resume auto** returns to the theme's track | |
| AU-4 | Settings > Linear crossfade on, repeat AU-2 | Audible mid-fade dip compared with equal-power (expected) | |
| AU-5 | Settings > Crossfade length 0.5 s and 8 s | Fades follow the setting | |
| AU-6 | Pause, then Play | A 300 ms fade-in, no pop (check on Bluetooth) | |
| AU-7 | Unplug wired headphones / disconnect Bluetooth | Pauses immediately | |
| AU-8 | Play a notification sound from another app | Volume ducks to about 20% and returns | |
| AU-9 | Receive a phone call | Pauses; resumes after the call | |
| AU-10 | Start another music app | This app pauses and does **not** resume by itself | |
| AU-11 | Pause, then in a notification-ducking moment | Nothing resumes unexpectedly | |
| AU-12 | Bluetooth or car **Next**/**Previous** | Enters Manual Override; Next walks the theme's tracks in order, Previous walks back | |
| AU-13 | Notification Play/Pause/Next | Works and stays in sync with Home | |
| AU-14 | Swipe the app away while playing | Music continues | |
| AU-15 | Swipe the app away while paused | Service stops (no lingering notification) | |
| AU-16 | Crossfade to a track with a dead URL (edit a manifest entry) | Stays on the current track, shows "Couldn't load...", retries once ~3 s later | |
| AU-17 | Let each track loop at least twice | No audible gap at the loop point (if there is, re-encode as Opus/OGG) | |
| AU-18 | Volume slider | Changes this app's level independently of system volume | |
| AU-19 | Two taps on Play very quickly | No crash, no double audio | |
| AU-20 | Lock the screen for 30 minutes of playback | No stutter; playback is stable | |

## 6. Zones, weather, time of day

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| CX-1 | Zones > press and hold the map | Haptic; a zone appears at that spot, selected; message "Added Zone N on the map." | |
| CX-2 | **Add zone here** | Zone at your location | |
| CX-3 | Change radius with the slider | Circle on the map changes on release; limits 100 m to 500 m, default 150 m | |
| CX-4 | Rename, change music, disable, delete | Each works; delete asks for confirmation | |
| CX-5 | Without a Maps key | Zones shows "The map isn't set up" and the list still works | |
| CX-6 | Assign a distinct track to a zone, press Play, walk in | After the 15 s dwell, crossfade to the zone's track; Home shows the zone name; Zones shows **Inside** | |
| CX-7 | Walk out | About 15 s grace, then crossfade back to the theme default | |
| CX-8 | Step in and straight back out within the grace window | Music does not flap | |
| CX-9 | With Settings > Faster zone detection **on**, app open | Arrival/leaving noticed within seconds of the dwell | |
| CX-10 | Same with it **off** | Detected by the platform geofence only (a minute or more is normal) | |
| CX-11 | App closed, screen off, walk in | Music switches (needs all-the-time location and battery exemption) | |
| CX-12 | Tap a Library track (Manual pick) then enter a different zone | Manual Override ends; the zone's track plays | |
| CX-13 | Two overlapping zones | The one you entered most recently wins; leaving it falls back to the other | |
| CX-14 | Debug panel: pretend **Rain**, then **Night** | A rain- or night-tagged variant of the playing slot is chosen; **Real** returns to normal | |
| CX-15 | Move the clock across 18:00 | Chip flips Day to Night; day/night variants switch | |
| CX-16 | Real rain (or snow) | A rain or snow variant plays if the slot has one; otherwise the base track | |
| CX-17 | Airplane mode | Zones keep working; weather counts as clear | |
| CX-18 | Settings > weather override off (debug panel note) | Weather treated as clear | |

## 7. Reliability

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| RL-1 | Reboot with a zone registered, open the app | Zones says it is watching; **no music starts by itself** after boot (Android 15 rule) | |
| RL-2 | While playing, `adb shell am kill com.example.personabgm`, then cross a zone boundary | Music comes back (placeholder notification first, then the media notification) | |
| RL-3 | Same, but you paused before killing | Music does **not** restart | |
| RL-4 | Same, but another app took audio focus first | Music does **not** restart | |
| RL-5 | Forbid background start (OEM battery saver) | You get a "Tap to resume your music" notification | |
| RL-6 | Update the app over itself | Geofences re-register (Zones says watching) | |
| RL-7 | Samsung/Xiaomi: leave the phone idle overnight with zones set | Zones still fire the next morning; if not, follow the OEM advice in Settings > Reliability | |
| RL-8 | Android 11+: leave the app unused for a long time (or toggle auto-revoke) | Permissions survive if "Keep permissions" was turned off | |

## 8. Accessibility

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| AC-1 | TalkBack on: swipe through Home | Headings announced as headings; theme cards read as radio buttons with selected state; swatches ignored | |
| AC-2 | TalkBack: press Play | "Crossfading..." and errors are announced | |
| AC-3 | TalkBack: Settings | Switch rows read as one switch each; sliders announce their name and value | |
| AC-4 | TalkBack: onboarding | "Step N of M" read once; each step title is a heading; status ("Allowed"/"Not yet") is text, not just color | |
| AC-5 | System font size at the largest setting | No clipped text; buttons grow; chips wrap or truncate cleanly; screens scroll | |
| AC-6 | Display size at the largest setting | Same | |
| AC-7 | Zones with TalkBack | The map is described as one element; every zone is reachable in the list below it | |
| AC-8 | Switch Access or keyboard | Every action reachable | |
| AC-9 | Look at all three themes outdoors/bright | Body text and buttons readable (contrast ratios are unit-tested) | |

## 9. Performance and battery

Measure before tuning; spec 9 asks for targets from real numbers. Use a phone you can leave alone, Wi-Fi, screen off.

| ID | Procedure | Record | Result |
| :-- | :--- | :--- | :-- |
| PF-1 | Fully charge, reset stats (`adb shell dumpsys batterystats --reset`), play for 1 hour screen-off with 3 zones registered and none entered | Battery % used; `adb shell dumpsys batterystats <package>` wakeups, wakelock time, location requests | |
| PF-2 | Repeat with no playback, zones registered, idle 8 hours | % used; the app should be almost invisible | |
| PF-3 | Android Studio **Energy Profiler** during a crossfade | CPU spike duration and wakelocks around the fade | |
| PF-4 | Cold start time (`adb shell am start -W`) on debug and releaseCheck | Total time; compare | |
| PF-5 | Crossfade smoothness: no audible stepping at the 16 ms volume ticks | Subjective + CPU use | |
| PF-6 | Fast zone detection on vs off, app open 10 minutes | Battery difference (it only runs while the app is on screen) | |

Suggested targets (adjust after measuring): PF-2 under 1% per 8 idle hours; PF-1 playback cost dominated by audio decoding, not by this app's own wakeups.

## 10. Release build checks (run on `releaseCheck`)

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| RB-1 | Launch and finish setup | No crash | |
| RB-2 | Library loads from your manifest | The manifest decodes (serialization keep rules work) | |
| RB-3 | Zones screen with a zone; **Re-register** equivalents (add a zone) | Geofence registration works | |
| RB-4 | Wait for a pre-cache download | The worker runs (WorkManager reflection keep rules work) | |
| RB-5 | Cross a zone boundary | Receiver runs, music switches | |
| RB-6 | Weather: wait for a refresh (watch logcat `WeatherRepository`) | Open-Meteo response decodes | |
| RB-7 | Check `app/build/outputs/mapping/releaseCheck/mapping.txt` exists | Keep it with each release to decode crash traces | |

## Private Cloudflare delivery and adaptive cache

| ID | Steps | Expected | Result |
| :-- | :--- | :--- | :-- |
| CF-1 | Keep R2 public access disabled; request `/v1/manifest` without a token | Worker returns 401 without exposing the manifest | |
| CF-2 | Enter an invalid token, then refresh Library | Worker returns 401 and app explains how to configure private access | |
| CF-3 | Enter the valid token and refresh Library | Manifest loads; token persists encrypted on the device | |
| CF-4 | Put an audio URL on another host in the manifest | App rejects the URL and never sends the bearer token to that host | |
| CF-5 | Play an uncached track and seek | Worker returns correct 206 / Content-Range responses | |
| CF-6 | Compare uploaded audio and streamed bytes | Same original object bytes; no transcoding or bitrate conversion | |
| CF-7 | Change cache limit between 256 MiB and 2 GiB while playing | Cache adjusts; loaded/incoming tracks remain protected | |
| CF-8 | Change Persona theme | Pre-cache is re-scheduled for the selected theme and zone tracks | |
| CF-9 | Cache a track, enable airplane mode and replay | Cached track remains playable offline | |
| CF-10 | Rotate Worker secret | Old token fails; entering new token restores access | |
