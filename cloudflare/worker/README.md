# Private music storage for PersonaMusicPlayer

This setup keeps the R2 bucket private. Android talks to a small Cloudflare Worker; the Worker verifies a single-user bearer token, then reads the manifest or streams original audio bytes from R2. No VPS or always-running application server is required.

## Request flow

```text
PersonaMusicPlayer (Android)
  GPS / geofences / weather/time / resolver / preferences / playback: on device
  HTTPS + Authorization: Bearer <device token>
          |
          v
Cloudflare Worker (auth + byte-range proxy)
          |
          v
Private Cloudflare R2 bucket (manifest.json + audio/**)
```

The Worker does not transcode, normalize, or re-encode audio. It returns bytes as stored and supports HTTP byte ranges for seeking and efficient ExoPlayer buffering.

## 1. Create the private R2 bucket

```bash
npx wrangler login
npx wrangler r2 bucket create persona-music
```

Keep public access disabled. Do not enable a public `r2.dev` URL or put R2 credentials in the Android app. The Worker accesses the bucket through `MUSIC_BUCKET` in `wrangler.toml`.

## 2. Create the owner token

Generate 32 random bytes as 64 hexadecimal characters:

```bash
openssl rand -hex 32
```

Windows PowerShell alternative:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$token = [BitConverter]::ToString($bytes).Replace("-", "").ToLowerInvariant()
$rng.Dispose()
$token
```

Set it as an encrypted Worker secret:

```bash
npx wrangler secret put PERSONA_ACCESS_TOKEN
```

Paste the generated value. Never put it in `wrangler.toml`, Android `BuildConfig`, committed `.env` files, screenshots or logs. For local Worker development only, copy `.dev.vars.example` to `.dev.vars`; that filename is git-ignored. Anyone who obtains the bearer token can access the private music API until you rotate the secret.

## 3. Test and deploy the Worker

```bash
npm install
npm test
npx wrangler deploy
```

Endpoints:

- `GET /v1/health` — authenticated health check
- `GET|HEAD /v1/manifest` — authenticated manifest with ETag revalidation
- `GET|HEAD /v1/audio/<object-path>` — authenticated audio delivery with HTTP Range support

The manifest is stored at the R2 bucket root. Audio URLs map to keys under `audio/`:

```text
Worker URL: https://YOUR-WORKER.workers.dev/v1/audio/p3/color_your_night.m4a
R2 key:     audio/p3/color_your_night.m4a
```

Private responses use `Cache-Control: private, no-store`; do not put an authenticated cache in front of this endpoint unless it is correctly configured to respect authorization.

## 4. Upload original audio and a v2 manifest

Upload original files unchanged. Example layout:

```text
manifest.json
audio/p3/color_your_night.m4a
audio/p4/heartbeat_heartbreak.m4a
audio/p5/tokyo_daylight.m4a
```

An individual upload example using Wrangler:

```bash
npx wrangler r2 object put persona-music/manifest.json --file=examples/manifest.example.json --content-type=application/json
npx wrangler r2 object put persona-music/audio/p3/color_your_night.m4a --file=/path/to/color_your_night.m4a --content-type=audio/mp4
```

For a large library, use the Cloudflare dashboard or an S3-compatible client such as rclone for bulk transfer. Keep R2 credentials only in your local tool configuration. Use an appropriate content type (`audio/mpeg` for MP3, commonly `audio/mp4` for M4A). Include `size_bytes` in track metadata where known. The sample manifest is not a playable library: replace the hostname, paths and metadata to match objects you actually upload.

Only upload music you have the right to store and use.

## 5. Configure Android

Add this to your local, git-ignored `local.properties`:

```properties
MUSIC_API_BASE_URL=https://YOUR-WORKER.workers.dev
```

It must be an HTTPS origin only (no path, userinfo, query or fragment). The app derives `/v1/manifest` and validates audio URLs to the same Worker host under `/v1/audio/`.

Build and install the app. Open **Settings → Private music library**, paste the 64-character token generated above, and save it. The app encrypts the token at rest with Android Keystore. It is not compiled into the APK or stored in DataStore.

## 6. Cache behaviour

- 1 GiB default; configurable from 256 MiB to 2 GiB in Settings.
- Persistent LRU eviction; the selected cache budget is applied after restart and when changed.
- The currently loaded track (including while paused) and incoming crossfade track are protected from eviction.
- Clearing saved audio removes other tracks immediately and defers removal of protected tracks until playback releases them.
- Pre-cache prioritizes the selected theme's roaming default and zone-assigned tracks/variants.
- Wi-Fi-only pre-cache remains enabled by default.
- Cached tracks remain playable offline; the cache is a working set, not a permanent mirror of R2.

## Security checklist

- [ ] R2 bucket stays private; public access is disabled.
- [ ] `PERSONA_ACCESS_TOKEN` is set as a Worker secret.
- [ ] The real token is not committed or embedded in the APK.
- [ ] `local.properties` and `.dev.vars` remain untracked.
- [ ] All manifest audio URLs use the exact configured Worker host and `/v1/audio/` path.
- [ ] Worker tests pass.
- [ ] Test byte-range seeking, background playback, crossfade, cached playback and offline behaviour on the target phone.
- [ ] Rotate the Worker secret if the phone is lost or the token may have been exposed.
