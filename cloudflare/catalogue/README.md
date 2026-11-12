# Persona BGM — R2 folder-driven manifest generator

This local utility scans the private R2 bucket's existing Persona folders and generates a static schema-v2 `manifest.json` for the Android app. It does **not** change the Worker, make the bucket public, download audio, transcode audio, or use the Android Worker bearer token.

## What it reads

| R2 prefix | Android `game_theme` |
|---|---|
| `audio/P3/` | `PERSONA_3_RELOAD` |
| `audio/P4/` | `PERSONA_4_REVIVAL` |
| `audio/P5/` | `PERSONA_5_ROYAL` |

It scans every `ListObjectsV2` page for each prefix. Nested folders are supported. `audio/test/` is intentionally excluded from the catalogue.

## What it writes

- Stable IDs based on the exact, case-sensitive R2 object key (filename slug + SHA-256 suffix).
- Display title from the filename only. The utility does not invent an artist, duration, lyrics, or artwork.
- `time_of_day: ALL` and `weather: ALL` until tags are deliberately curated.
- `size_bytes` from R2's object listing metadata.
- `stream_url` for the current Worker route. Each path segment is percent-encoded independently; slashes remain path separators and case is unchanged.
- `theme_defaults` pointing to actual tracks in the same Persona theme.

The default file order is deterministic by exact object key. Unless you supply `theme-defaults.json`, the first sorted track in each non-empty folder is used provisionally as its theme default and the report says so. You can override defaults by copying `theme-defaults.example.json` to `theme-defaults.json` and putting an exact R2 object key (not a URL) in any populated theme field. Use `null` to retain automatic selection. That file is ignored by Git.

**Empty Persona folders are supported.** For example, if `audio/P4/` is empty because Persona 4 Revival has not released yet, the generator reports that folder as empty, omits `PERSONA_4_REVIVAL` from `theme_defaults`, and still generates/uploads the available P3/P5 tracks. The Android validator explicitly supports a theme with no tracks and no roaming default. When P4 songs are added later, the same generator automatically adds them and chooses a provisional P4 default (or uses your configured override). The generator still refuses to create an entirely empty catalogue if no supported songs exist in any Persona folder.

The generated file is written to `cloudflare/catalogue/output/manifest.json`, which is ignored by Git. The local manifest is validated against the current Android DTO/validator rules before it is written.

## Requirements

- Node.js 20 or newer.
- A bucket-scoped Cloudflare R2 S3 API token with **Object Read & Write** access for only the `persona-music` bucket.
- The repository root `local.properties` must contain the existing `MUSIC_API_BASE_URL=https://...workers.dev` line, or set `MUSIC_API_BASE_URL` in the process environment.

Create an R2 API token in Cloudflare Dashboard → R2 → Manage API Tokens. Select **Object Read & Write** and restrict it to the `persona-music` bucket. This is a separate credential from the Android `PERSONA_ACCESS_TOKEN` Worker secret. Do not send either credential in chat and do not put the R2 credentials in a file or commit them.

## Install and dry run (PowerShell)

From the repository root:

```powershell
npm --prefix cloudflare/catalogue install
.\cloudflare\catalogue\run.ps1
```

The PowerShell helper prompts for the Account ID, Access Key ID and hidden Secret Access Key. It clears the process environment variables after Node exits. The normal command is a **dry run**: it lists objects, reports counts/skips/defaults, writes the local JSON and does not upload anything.

Review the output file:

```powershell
Get-Content .\cloudflare\catalogue\output\manifest.json
```

Also review the report for each prefix. Empty folders are informational and do not block upload, provided at least one supported track exists across the catalogue.

## Upload the static manifest

Only after reviewing the local output:

```powershell
.\cloudflare\catalogue\run.ps1 -Upload
```

The script prints the destination and requires the exact typed confirmation:

```text
UPLOAD persona-music/manifest.json
```

It uploads only `manifest.json` at the bucket root with `Content-Type: application/json` and `Cache-Control: private, no-store`. It does not send an ACL, modify bucket settings, change R2 public access, or touch audio objects. It then reads the object back through the S3 API and compares the bytes with the generated file.

## Later uploads

1. Upload new audio files into the existing exact-case Persona folder (`audio/P3/`, `audio/P4/` or `audio/P5/`).
2. Run `run.ps1` to regenerate locally.
3. Review counts, skipped objects, duplicate titles and theme defaults.
4. Run `run.ps1 -Upload` after the dry run is correct.
5. Refresh the app's Library/manifest in Persona BGM.

No 160+ hand-written JSON entries are required.

## Notes and limits

- Supported filename extensions are based on the Media3 ExoPlayer progressive container list. Actual sample codecs can still vary by file and Android device; playback must be tested on the emulator/phone.
- Artist is intentionally omitted because `ListObjectsV2` does not contain audio tags. A future metadata pass can read embedded tags without altering the audio, but this generator does not download every large audio file to do so.
- `audio/test/` is not part of the three Persona catalogues.
- A Persona folder may intentionally have zero supported tracks; its theme default is omitted until at least one supported song is present.
- An unsupported extension, zero-size object, folder marker, or key the present Worker route rejects is reported and skipped.
- Duplicate display titles are reported but do not collide because IDs include a hash of the full exact key.
- A truncated list response without a fresh continuation token is a hard error. The utility will never silently write a first-page-only catalogue.
- The current Worker maps `/v1/audio/<key after audio/>` to `audio/<decoded key after audio/>`. Do not lowercase or otherwise normalize an object key.
- Original audio objects are never downloaded or changed by this tool. The only upload is the small static manifest JSON.
