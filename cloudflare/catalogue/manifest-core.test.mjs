import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildManifest,
  normalizeWorkerBaseUrl,
  stableTrackId,
  titleFromObjectKey,
  workerAudioUrl,
  listAllObjects,
} from './manifest-core.mjs';

const BASE = 'https://persona-music-api.example.workers.dev';

function objects() {
  return {
    PERSONA_3_RELOAD: [
      { Key: 'audio/P3/Color_Your_Night.mp3', Size: 1000 },
      { Key: 'audio/P3/Nested/Its Going Down Now.m4a', Size: 2000 },
      { Key: 'audio/P3/', Size: 0 },
      { Key: 'audio/P3/readme.txt', Size: 10 },
      { Key: 'audio/P3/empty.mp3', Size: 0 },
    ],
    PERSONA_4_REVIVAL: [
      { Key: 'audio/P4/Heartbeat_Heartbreak.m4a', Size: 3000 },
    ],
    PERSONA_5_ROYAL: [
      { Key: 'audio/P5/Tokyo Daylight.mp3', Size: 4000 },
      { Key: 'audio/P5/曲 名.flac', Size: 5000 },
    ],
  };
}

test('Worker base URL is constrained to an HTTPS origin', () => {
  assert.equal(normalizeWorkerBaseUrl('https://worker.example/'), 'https://worker.example');
  assert.throws(() => normalizeWorkerBaseUrl('http://worker.example'), /HTTPS origin/);
  assert.throws(() => normalizeWorkerBaseUrl('https://worker.example/v1'), /HTTPS origin/);
  assert.throws(() => normalizeWorkerBaseUrl('https://user:pass@worker.example'), /HTTPS origin/);
});

test('Worker URL encodes segments but keeps exact key casing and nested separators', () => {
  assert.equal(
    workerAudioUrl(BASE, "audio/P3/Folder Name/It's Going Down Now.mp3"),
    `${BASE}/v1/audio/P3/Folder%20Name/It%27s%20Going%20Down%20Now.mp3`,
  );
  assert.equal(workerAudioUrl(BASE, 'audio/P5/曲 名.flac'), `${BASE}/v1/audio/P5/%E6%9B%B2%20%E5%90%8D.flac`);
  assert.throws(() => workerAudioUrl(BASE, 'audio/P3/../secret.mp3'), /cannot be served/);
});

test('titles are derived from filenames without inventing artist metadata', () => {
  assert.equal(titleFromObjectKey('audio/P3/Color_Your-Night.mp3'), 'Color Your Night');
});

test('stable IDs depend on the exact case-sensitive object key', () => {
  const config = { idPrefix: 'p3' };
  assert.equal(stableTrackId(config, 'audio/P3/song.mp3'), stableTrackId(config, 'audio/P3/song.mp3'));
  assert.notEqual(stableTrackId(config, 'audio/P3/song.mp3'), stableTrackId(config, 'audio/p3/song.mp3'));
});

test('builds a v2 catalogue with size, truthful ALL tags, stable IDs and valid defaults', () => {
  const { manifest, report } = buildManifest({ objectsByTheme: objects(), workerBaseUrl: BASE });
  assert.equal(manifest.version, 2);
  assert.equal(manifest.tracks.length, 5);
  assert.equal(manifest.tracks.every((track) => track.time_of_day === 'ALL' && track.weather === 'ALL'), true);
  assert.equal(manifest.tracks.every((track) => !Object.hasOwn(track, 'artist')), true);
  assert.equal(manifest.tracks.find((track) => track.title === 'Color Your Night').size_bytes, 1000);
  assert.equal(manifest.theme_defaults.PERSONA_3_RELOAD, manifest.tracks.find((track) => track.game_theme === 'PERSONA_3_RELOAD').id);
  assert.equal(manifest.theme_defaults.PERSONA_4_REVIVAL, manifest.tracks.find((track) => track.game_theme === 'PERSONA_4_REVIVAL').id);
  assert.equal(manifest.theme_defaults.PERSONA_5_ROYAL, manifest.tracks.find((track) => track.game_theme === 'PERSONA_5_ROYAL').id);
  assert.equal(report.includedByTheme.PERSONA_3_RELOAD, 2);
  assert.equal(report.skippedByTheme.PERSONA_3_RELOAD.folder_placeholder, 1);
  assert.equal(report.skippedByTheme.PERSONA_3_RELOAD.unsupported_extension, 1);
  assert.equal(report.skippedByTheme.PERSONA_3_RELOAD.missing_invalid_or_zero_size, 1);
});

test('default overrides require an exact included key from the matching Persona folder', () => {
  const { manifest, report } = buildManifest({
    objectsByTheme: objects(),
    workerBaseUrl: BASE,
    themeDefaults: { PERSONA_3_RELOAD: 'audio/P3/Nested/Its Going Down Now.m4a' },
  });
  assert.equal(report.selectedDefaults.PERSONA_3_RELOAD.selection, 'configured');
  assert.equal(manifest.theme_defaults.PERSONA_3_RELOAD, manifest.tracks.find((track) => track.title === 'Its Going Down Now').id);
  assert.throws(() => buildManifest({
    objectsByTheme: objects(),
    workerBaseUrl: BASE,
    themeDefaults: { PERSONA_3_RELOAD: 'audio/P3/not-uploaded.mp3' },
  }), /not an included exact R2 key/);
});

test('duplicate display titles are reported while stable IDs remain distinct', () => {
  const source = objects();
  source.PERSONA_3_RELOAD.push({ Key: 'audio/P3/Remixes/Color_Your_Night.mp3', Size: 99 });
  const { manifest, report } = buildManifest({ objectsByTheme: source, workerBaseUrl: BASE });
  assert.equal(manifest.tracks.filter((track) => track.title === 'Color Your Night').length, 2);
  assert.equal(report.titleCollisions.length, 1);
  assert.equal(report.idCollisions.length, 0);
});

test('ListObjectsV2 listing follows every continuation token and fails on broken pagination', async () => {
  const requests = [];
  const client = {
    async send(command) {
      requests.push(command.input);
      if (requests.length === 1) {
        return { Contents: [{ Key: 'audio/P3/a.mp3', Size: 1 }], IsTruncated: true, NextContinuationToken: 'next-page' };
      }
      return { Contents: [{ Key: 'audio/P3/b.mp3', Size: 2 }], IsTruncated: false };
    },
  };
  class FakeListObjectsV2Command { constructor(input) { this.input = input; } }
  const listed = await listAllObjects(client, 'persona-music', 'audio/P3/', FakeListObjectsV2Command);
  assert.equal(listed.length, 2);
  assert.equal(requests[0].Prefix, 'audio/P3/');
  assert.equal(requests[1].ContinuationToken, 'next-page');
  assert.equal(requests[0].MaxKeys, 1000);

  const broken = {
    async send() { return { Contents: [], IsTruncated: true, NextContinuationToken: undefined }; },
  };
  await assert.rejects(listAllObjects(broken, 'persona-music', 'audio/P3/', FakeListObjectsV2Command), /invalid\/repeated pagination token/);
});

test('an intentionally empty Persona folder is allowed and omitted from theme defaults', () => {
  const source = objects();
  source.PERSONA_4_REVIVAL = [{ Key: 'audio/P4/', Size: 0 }];
  const { manifest, report } = buildManifest({ objectsByTheme: source, workerBaseUrl: BASE });

  assert.equal(manifest.tracks.length, 4);
  assert.equal(manifest.tracks.some((track) => track.game_theme === 'PERSONA_4_REVIVAL'), false);
  assert.equal(Object.hasOwn(manifest.theme_defaults, 'PERSONA_4_REVIVAL'), false);
  assert.deepEqual(report.emptyThemes, ['PERSONA_4_REVIVAL']);
  assert.equal(report.selectedDefaults.PERSONA_3_RELOAD !== undefined, true);
  assert.equal(report.selectedDefaults.PERSONA_5_ROYAL !== undefined, true);
});

test('a Persona folder automatically joins the catalogue when songs are added later', () => {
  const source = objects();
  source.PERSONA_4_REVIVAL = [{ Key: 'audio/P4/', Size: 0 }];
  const empty = buildManifest({ objectsByTheme: source, workerBaseUrl: BASE });
  assert.equal(Object.hasOwn(empty.manifest.theme_defaults, 'PERSONA_4_REVIVAL'), false);

  source.PERSONA_4_REVIVAL.push({ Key: 'audio/P4/Heartbeat_Heartbreak.m4a', Size: 3000 });
  const populated = buildManifest({ objectsByTheme: source, workerBaseUrl: BASE });
  const p4Track = populated.manifest.tracks.find((track) => track.game_theme === 'PERSONA_4_REVIVAL');
  assert.ok(p4Track);
  assert.equal(populated.manifest.theme_defaults.PERSONA_4_REVIVAL, p4Track.id);
  assert.deepEqual(populated.report.emptyThemes, []);
});

test('all three Persona folders may not be empty at once', () => {
  const source = {
    PERSONA_3_RELOAD: [{ Key: 'audio/P3/', Size: 0 }],
    PERSONA_4_REVIVAL: [{ Key: 'audio/P4/', Size: 0 }],
    PERSONA_5_ROYAL: [{ Key: 'audio/P5/', Size: 0 }],
  };
  assert.throws(
    () => buildManifest({ objectsByTheme: source, workerBaseUrl: BASE }),
    /No supported audio objects were found/,
  );
});
