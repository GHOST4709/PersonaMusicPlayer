# Specification: Persona Context-Aware BGM Mobile Application

## 1. Project Overview
A native Android application inspired by the *Persona* video game series (P3 Reload, P4 Revival, P5 Royal). The app streams location-aware background music (BGM) while running in the background. It dynamically changes tracks based on GPS Geofence regions, Time of Day, Weather, and active Game Theme, while managing crossfading, offline caching, and hardware audio routing.

---

## 2. Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Single Activity Architecture)
- **Audio Engine**: Jetpack Media3 (`ExoPlayer`, `MediaSessionService`, `SimpleCache`)
- **Location & Geofencing**: Google Play Services (`GeofencingClient`, `FusedLocationProviderClient`)
- **Database**: Room Database (Geofence zones, metadata, custom mappings)
- **Preferences**: Jetpack DataStore (Active theme, global volume, settings)
- **Network**: Retrofit + Kotlinx Serialization (Fetching JSON track manifest & Open-Meteo Weather API)
- **Dependency Injection**: Hilt
- **Architecture Pattern**: MVVM with Clean Architecture (UI -> ViewModel -> Repository -> Data Source)

---

## 3. Core Features & Design System

### 3.1 Theme Matrix (Dynamic Theme Switcher)
The app must support live theme switching between 3 distinct Persona aesthetics:

| Theme | Primary Color | Accent Color | Surface / Background | Aesthetic Style |
| :--- | :--- | :--- | :--- | :--- |
| **Persona 3 Reload** | Cobalt Blue (`#0055FF`) | Electric Cyan (`#00E5FF`) | Dark Slate (`#0A0E17`) | Sleek liquid cutouts, dark aquatic gradients |
| **Persona 4 Revival** | TV Yellow (`#FFD000`) | Pop Orange (`#FF6600`) | Off-Black (`#181818`) | CRT lines, pop-art badges, halftone dots |
| **Persona 5 Royal** | Crimson Red (`#D80027`) | Pure White (`#FFFFFF`) | Obsidian Black (`#101010`) | Slanted diagonal text, dynamic comic cutouts |

### 3.2 Quality of Life (QoL) & Loading States
- **Smooth Onboarding**: A step-by-step stylized setup flow for required permissions (Location -> Background Location -> Battery Optimization -> Bluetooth).
- **Loading States (LCE Pattern)**: Implement Loading, Content, and Error states for all network/database actions.
  - *Manifest Fetch*: Show a stylized "Connecting to Metaverse..." or "Tuning TV..." animated screen.
  - *Map Load*: Use skeleton shimmering matching the current theme's primary color before the map fully renders.
- **Haptic Feedback**: Implement `HapticFeedbackType.LongPress` and `TextHandleMove` via Compose for sharp, tactile feedback when navigating the UI, switching themes, or dropping a geofence pin.
- **Error Handling & Fallbacks**:
  - *No Internet*: Play from cached ExoPlayer tracks; disable weather overrides (fallback to clear weather logic).
  - *GPS Disabled*: Show a high-contrast in-app banner prompting the user to enable Location Services.

---

## 4. Audio Engine & Media3 Integration

### 4.1 Crossfading & Playback
- **Dual ExoPlayer Instances**: Maintain `ExoPlayerA` and `ExoPlayerB` inside `MediaSessionService`.
- **Crossfade Logic**: Upon location/context trigger, perform a linear volume crossfade over 2500ms (A: 1.0 -> 0.0, B: 0.0 -> 1.0).
- **Buffer Fade-In**: Automatically apply a 300ms volume fade-in when playback is manually resumed to prevent audio popping on Bluetooth devices.

### 4.2 Caching & Hardware Interruptions
- **Offline Caching**: Wrap ExoPlayer `DataSource` with Media3 `SimpleCache` (500 MB max disk limit) to eliminate repeated network downloads and ensure 0 latency on geofence triggers.
- **Hardware Routing**:
  - Handle `ACTION_AUDIO_BECOMING_NOISY` to auto-pause when AUX cables or Bluetooth headphones disconnect.
  - Handle `AUDIOFOCUS_GAIN`, `AUDIOFOCUS_LOSS` (pause), and `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` (lower volume to 20% for notifications).
  - Wire `MediaSession` to accept Bluetooth remote/car hardware key events (Play, Pause, Next).

---

## 5. Geofencing & Context Manager

- **Geofence Parameters**: Default radius = 75 meters. Debounce dwell time = 15 seconds hysteresis delay before track switching.
- **Context Priority Cascade**:
  1. **Weather Override**: (e.g., Open-Meteo returns Rain -> play rain variant track).
  2. **Time-of-Day Override**: Day (06:00–18:00) vs. Night (18:00–06:00).
  3. **Location Geofence Track**: User-assigned track for current zone.
  4. **Fallback (Roaming)**: Theme default background track when outside all zones.
- **Battery Optimization Rules**: Must direct users to `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` during onboarding so OEMs (Samsung/Xiaomi) do not kill the geofence listener.

---

## 6. Required Manifest Permissions & Services

```xml
<!-- Location Permissions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Foreground Audio & Bluetooth -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
