# PersonaMusicPlayer — Patch Report

## Target architecture

This patch applies the agreed personal, primarily single-user design:

- GPS, geofencing, weather/time evaluation, zones, preferences, music selection, playback and cache policy remain on the Android phone.
- The complete music catalogue and v2 manifest are stored in a **private Cloudflare R2 bucket**.
- Android requests the manifest and audio through a Cloudflare Worker. The Worker validates a single-user bearer token before accessing R2.
- Audio is delivered as stored. There is no 192 kbps conversion, transcoding or normalization step.
- The device token is encrypted at rest using Android Keystore. It is not included in BuildConfig, DataStore, resources or the APK.
- No login/account system or VPS is required. The Worker is a small authenticated edge endpoint.

## Android changes

- Added private music token settings and Android Keystore AES-GCM encryption for the 64-character owner token.
- The shared OkHttp client attaches the bearer token only to the configured HTTPS Worker origin and `/v1/` routes. External weather API traffic does not receive the music token.
- Media3 uses a resolving data source to add the bearer token to audio requests. It rejects audio URLs outside the configured HTTPS Worker host and `/v1/audio/` path.
- Manifest validation enforces the same private Worker host/path allow-list, rejecting third-party hosts, insecure URLs, query strings, fragments, userinfo and unexpected ports.
- Replaced the fixed 500 MiB cache with a configurable persistent LRU cache: 1 GiB default, 256 MiB–2 GiB configurable in Settings.
- The loaded track (including while paused) and incoming crossfade track are protected from eviction. If a cache clear is requested, those tracks are removed only after playback releases them.
- Pre-caching prioritizes the selected Persona theme default and zone-assigned tracks/variants; Wi-Fi-only pre-caching remains enabled by default.
- Changing the active theme reschedules pre-caching.
- Authentication failures (401/403) show guidance to configure the private token. Saving the token queues a manifest retry even if the first request is still running.
- Updated root manifest and debug seed examples to v2 Worker URL paths. The URLs are placeholders and need to be replaced with the deployed Worker hostname and uploaded object keys.

## Cloudflare Worker added

Location: `cloudflare/worker/`

- `GET|HEAD /v1/manifest`: authenticated manifest retrieval with ETag revalidation.
- `GET|HEAD /v1/audio/<object-path>`: authenticated R2 audio delivery with HTTP byte-range support for seeking and streaming.
- Audio object paths are mapped below the R2 `audio/` prefix; traversal and malformed paths are rejected.
- Private responses use `Cache-Control: private, no-store` and `Vary: Authorization`.
- R2 remains private; do not enable public bucket access or publish an `r2.dev` URL.
- The owner token is configured as a Worker secret (`PERSONA_ACCESS_TOKEN`), not in the repository or Android binary.

Deployment and upload instructions are in `cloudflare/worker/README.md`.

## Cache defaults

- 1 GiB default budget; 256 MiB–2 GiB configurable range.
- Persistent LRU order uses cache span touch timestamps.
- Current/incoming playback tracks are pinned so a trim does not interrupt playback or crossfade.
- The cache is a working set, not a permanent copy of the entire R2 library.
- The selected theme default and zone-assigned tracks/variants are pre-cached; Wi-Fi-only remains the default.

## Checks run in this environment

- Cloudflare Worker unit tests: **11 passed** (`cd cloudflare/worker && npm test`, Node.js v22.16.0).
- Worker source and tests pass `node --check`.
- All 4 project JSON files parse successfully.
- Both project TOML files parse successfully.
- `git diff --check` is clean for the Android source/config, Worker, and updated test-plan files.
- Archive integrity and required source paths were verified before delivery.

## Not verified here

- The Android/Kotlin project was **not compiled or run**. The uploaded source does not include the Gradle wrapper scripts/JAR, and this environment has no installed Gradle distribution or Android SDK (`ANDROID_HOME` is unset). Android-specific compilation, Hilt/KSP processing, Media3 integration and Android unit tests still need to be run in Android Studio or with the project's toolchain.
- The Worker unit tests use an R2 mock. The Worker has not been deployed to the owner's Cloudflare account and has not been tested against a live R2 bucket.
- Physical-device validation is still required for playback startup, Range seeking, crossfade timing, cache eviction, airplane-mode playback, background operation, geofencing and battery behaviour.

## Before first use

1. Follow `cloudflare/worker/README.md` to create the private `persona-music` R2 bucket, generate a random 32-byte token, set `PERSONA_ACCESS_TOKEN` with Wrangler and deploy the Worker.
2. Upload your original audio objects under `audio/...` and a real v2 `manifest.json` at the bucket root. Replace all placeholder hostnames and paths with your deployed Worker and actual R2 keys. Do not upload the sample manifest unchanged.
3. Add `MUSIC_API_BASE_URL=https://YOUR-WORKER.workers.dev` to the untracked Android root `local.properties` file. Keep any Maps key there too.
4. Generate the Gradle wrapper using Gradle 9.4.1 (`gradle wrapper --gradle-version 9.4.1`) or build from a compatible Android Studio installation. Run `:app:testDebugUnitTest`, `:app:assembleDebug` and `:app:assembleReleaseCheck`.
5. Install the app, open **Settings → Private music library**, enter the Worker token, then follow the device checklist in `docs/TEST_PLAN.md`.

The bearer token is a single-user shared secret. Anyone who obtains it can access the private music API until the Worker secret is rotated. Keep it out of source control, screenshots, logs and chat messages.

Only upload music you have the rights to store and use.
