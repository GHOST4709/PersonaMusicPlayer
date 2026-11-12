import { createHash } from 'node:crypto';
import path from 'node:path';

export const PERSONA_FOLDERS = Object.freeze([
  Object.freeze({
    prefix: 'audio/P3/',
    theme: 'PERSONA_3_RELOAD',
    idPrefix: 'p3',
  }),
  Object.freeze({
    prefix: 'audio/P4/',
    theme: 'PERSONA_4_REVIVAL',
    idPrefix: 'p4',
  }),
  Object.freeze({
    prefix: 'audio/P5/',
    theme: 'PERSONA_5_ROYAL',
    idPrefix: 'p5',
  }),
]);

// These are containers/extractors supported by Media3 ExoPlayer's progressive playback path.
// Actual sample-codec support can still depend on the audio stream and Android device.
export const DEFAULT_AUDIO_EXTENSIONS = Object.freeze([
  '.mp1', '.mp2', '.mp3',
  '.mp4', '.m4a', '.m4b', '.fmp4',
  '.aac', '.adts',
  '.ogg', '.oga', '.opus',
  '.wav', '.flac',
  '.webm', '.mkv', '.mka',
  '.ts', '.mpg', '.mpeg',
  '.amr', '.3gp',
  '.flv',
]);

const THEME_SET = new Set(PERSONA_FOLDERS.map((item) => item.theme));

export function normalizeWorkerBaseUrl(value) {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error('MUSIC_API_BASE_URL is missing. Add it to the repository root local.properties.');
  }

  let parsed;
  try {
    parsed = new URL(value.trim());
  } catch {
    throw new Error('MUSIC_API_BASE_URL is not a valid URL.');
  }

  if (
    parsed.protocol !== 'https:' ||
    !parsed.hostname ||
    parsed.username ||
    parsed.password ||
    parsed.search ||
    parsed.hash ||
    (parsed.pathname !== '/' && parsed.pathname !== '') ||
    (parsed.port && parsed.port !== '443')
  ) {
    throw new Error('MUSIC_API_BASE_URL must be an HTTPS origin only (no path, credentials, query, or fragment).');
  }

  return parsed.origin;
}

function validateWorkerKey(key) {
  if (typeof key !== 'string' || !key.startsWith('audio/')) return false;
  const segments = key.split('/');
  if (segments[0] !== 'audio' || segments.length < 2) return false;
  return segments.every((segment, index) => {
    if (!segment || segment === '.' || segment === '..') return false;
    if (segment.includes('\\') || segment.includes('\0') || segment.includes('/')) return false;
    return index === 0 ? segment === 'audio' : true;
  });
}

function encodePathSegment(segment) {
  return encodeURIComponent(segment).replace(/[!'()*]/g, (character) =>
    `%${character.charCodeAt(0).toString(16).toUpperCase()}`,
  );
}

/**
 * Mirrors cloudflare/worker/src/index.js audioObjectKey():
 * /v1/audio/<encoded R2 key after audio/> becomes audio/<decoded path segments>.
 * Encode segments independently so separators remain separators and key casing is unchanged.
 */
export function workerAudioUrl(workerBaseUrl, objectKey) {
  const base = normalizeWorkerBaseUrl(workerBaseUrl);
  if (!validateWorkerKey(objectKey)) {
    throw new Error(`Object key cannot be served by the current Worker route: ${JSON.stringify(objectKey)}`);
  }

  const relativeSegments = objectKey.slice('audio/'.length).split('/');
  return `${base}/v1/audio/${relativeSegments.map(encodePathSegment).join('/')}`;
}

export function titleFromObjectKey(objectKey) {
  const filename = path.posix.basename(objectKey);
  const extension = path.posix.extname(filename);
  const withoutExtension = extension ? filename.slice(0, -extension.length) : filename;
  const title = withoutExtension
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return title;
}

function slugFromObjectKey(objectKey) {
  const filename = path.posix.basename(objectKey);
  const extension = path.posix.extname(filename);
  const withoutExtension = extension ? filename.slice(0, -extension.length) : filename;
  const slug = withoutExtension
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 36);
  return slug || 'track';
}

export function stableTrackId(themeConfig, objectKey) {
  // SHA-256 includes the exact case-sensitive key, including nested path segments.
  const suffix = createHash('sha256').update(objectKey, 'utf8').digest('hex').slice(0, 16);
  return `${themeConfig.idPrefix}_${slugFromObjectKey(objectKey)}_${suffix}`;
}

function compareKeys(left, right) {
  // Locale-independent ordering makes manifest order reproducible across machines.
  if (left < right) return -1;
  if (left > right) return 1;
  return 0;
}


/** Read every page. The command constructor is injected to keep this core independently testable. */
export async function listAllObjects(client, bucket, prefix, ListObjectsV2Command) {
  if (typeof ListObjectsV2Command !== 'function') {
    throw new Error('A ListObjectsV2Command constructor is required.');
  }
  const objects = [];
  const seenTokens = new Set();
  let continuationToken;
  let page = 0;

  do {
    page += 1;
    if (page > 10000) throw new Error(`Pagination safety limit reached while listing ${prefix}.`);

    const response = await client.send(new ListObjectsV2Command({
      Bucket: bucket,
      Prefix: prefix,
      MaxKeys: 1000,
      ...(continuationToken ? { ContinuationToken: continuationToken } : {}),
    }));

    objects.push(...(response.Contents ?? []));
    if (!response.IsTruncated) break;

    const nextToken = response.NextContinuationToken;
    if (!nextToken || seenTokens.has(nextToken) || nextToken === continuationToken) {
      throw new Error(`R2 returned an invalid/repeated pagination token for ${prefix}; refusing an incomplete manifest.`);
    }
    seenTokens.add(nextToken);
    continuationToken = nextToken;
  } while (true);

  return objects;
}

function increment(map, key) {
  map[key] = (map[key] ?? 0) + 1;
}

/**
 * Pure manifest builder. The input maps Persona theme names to R2 ListObjectsV2 Contents arrays.
 * No artist, duration, artwork, or tags are inferred: those fields are intentionally omitted.
 */
export function buildManifest({
  objectsByTheme,
  workerBaseUrl,
  themeDefaults = {},
  allowedExtensions = DEFAULT_AUDIO_EXTENSIONS,
}) {
  const normalizedBase = normalizeWorkerBaseUrl(workerBaseUrl);
  const extensionSet = new Set(allowedExtensions.map((extension) => extension.toLowerCase()));
  const report = {
    rawObjects: {},
    includedByTheme: {},
    skippedByTheme: {},
    skippedSamples: [],
    titleCollisions: [],
    idCollisions: [],
    selectedDefaults: {},
    emptyThemes: [],
  };
  const tracks = [];
  const objectKeyById = new Map();
  const normalizedTitles = new Map();

  for (const themeConfig of PERSONA_FOLDERS) {
    const objects = [...(objectsByTheme?.[themeConfig.theme] ?? [])]
      .sort((a, b) => compareKeys(a?.Key ?? '', b?.Key ?? ''));
    report.rawObjects[themeConfig.theme] = objects.length;
    report.includedByTheme[themeConfig.theme] = 0;
    report.skippedByTheme[themeConfig.theme] = {};
    const themeTracks = [];

    const skip = (reason, key) => {
      increment(report.skippedByTheme[themeConfig.theme], reason);
      if (report.skippedSamples.length < 20) {
        report.skippedSamples.push({ theme: themeConfig.theme, key: key ?? '(missing key)', reason });
      }
    };

    for (const object of objects) {
      const key = object?.Key;
      if (typeof key !== 'string' || !key.startsWith(themeConfig.prefix)) {
        skip('missing_or_wrong_prefix', key);
        continue;
      }
      if (key === themeConfig.prefix || key.endsWith('/')) {
        skip('folder_placeholder', key);
        continue;
      }
      if (!validateWorkerKey(key)) {
        skip('worker_rejects_key', key);
        continue;
      }

      const extension = path.posix.extname(key).toLowerCase();
      if (!extension || !extensionSet.has(extension)) {
        skip('unsupported_extension', key);
        continue;
      }

      const size = object.Size;
      if (!Number.isSafeInteger(size) || size <= 0) {
        skip('missing_invalid_or_zero_size', key);
        continue;
      }

      const title = titleFromObjectKey(key);
      if (!title) {
        skip('empty_title', key);
        continue;
      }

      let streamUrl;
      try {
        streamUrl = workerAudioUrl(normalizedBase, key);
      } catch {
        skip('worker_rejects_key', key);
        continue;
      }

      const id = stableTrackId(themeConfig, key);
      const previousKey = objectKeyById.get(id);
      if (previousKey && previousKey !== key) {
        report.idCollisions.push({ id, keys: [previousKey, key] });
        throw new Error(`Stable ID collision '${id}' for R2 keys ${JSON.stringify([previousKey, key])}. No manifest was written.`);
      }
      objectKeyById.set(id, key);

      const track = {
        id,
        game_theme: themeConfig.theme,
        title,
        time_of_day: 'ALL',
        weather: 'ALL',
        size_bytes: size,
        stream_url: streamUrl,
      };

      themeTracks.push(track);
      const titleIdentity = `${themeConfig.theme}\0${title.normalize('NFKC').toLocaleLowerCase('en-US')}`;
      const priorTitles = normalizedTitles.get(titleIdentity) ?? { title, keys: [] };
      priorTitles.keys.push(key);
      normalizedTitles.set(titleIdentity, priorTitles);
    }

    report.includedByTheme[themeConfig.theme] = themeTracks.length;
    if (themeTracks.length === 0) report.emptyThemes.push(themeConfig.theme);
    tracks.push(...themeTracks);
  }

  for (const [identity, collision] of normalizedTitles) {
    if (collision.keys.length > 1) {
      report.titleCollisions.push({
        theme: identity.split('\0')[0],
        title: collision.title,
        keys: collision.keys,
      });
    }
  }

  const defaults = {};
  for (const themeConfig of PERSONA_FOLDERS) {
    const themeTracks = tracks.filter((track) => track.game_theme === themeConfig.theme);
    if (themeTracks.length === 0) continue;

    const requestedKey = themeDefaults?.[themeConfig.theme];
    let chosen;
    let selection = 'first_sorted_object';

    if (typeof requestedKey === 'string' && requestedKey.trim()) {
      chosen = themeTracks.find((track) => track.stream_url === workerAudioUrl(normalizedBase, requestedKey.trim()));
      if (!chosen) {
        throw new Error(
          `Configured default for ${themeConfig.theme} is not an included exact R2 key: ${JSON.stringify(requestedKey)}.`,
        );
      }
      selection = 'configured';
    } else {
      chosen = themeTracks[0];
    }

    defaults[themeConfig.theme] = chosen.id;
    report.selectedDefaults[themeConfig.theme] = {
      id: chosen.id,
      title: chosen.title,
      stream_url: chosen.stream_url,
      object_key: objectKeyById.get(chosen.id),
      selection,
    };
  }

  if (tracks.length === 0) {
    throw new Error('No supported audio objects were found under audio/P3/, audio/P4/, or audio/P5/.');
  }

  const manifest = {
    version: 2,
    theme_defaults: defaults,
    tracks,
  };

  validateGeneratedManifest(manifest, normalizedBase);
  return { manifest, report };
}

/** A strict local check aligned with the current Android ManifestValidator contract. */
export function validateGeneratedManifest(manifest, workerBaseUrl) {
  const normalizedBase = normalizeWorkerBaseUrl(workerBaseUrl);
  if (manifest?.version !== 2) throw new Error('Generated manifest version must be 2.');
  if (!Array.isArray(manifest.tracks) || manifest.tracks.length === 0) {
    throw new Error('Generated manifest must contain at least one track.');
  }

  const ids = new Set();
  for (const [index, track] of manifest.tracks.entries()) {
    if (!track || typeof track.id !== 'string' || !track.id.trim()) {
      throw new Error(`Track #${index} has no valid id.`);
    }
    if (ids.has(track.id)) throw new Error(`Duplicate track id: ${track.id}`);
    ids.add(track.id);

    if (!THEME_SET.has(track.game_theme)) {
      throw new Error(`Track '${track.id}' has an unsupported game_theme.`);
    }
    if (typeof track.title !== 'string' || !track.title.trim()) {
      throw new Error(`Track '${track.id}' has no title.`);
    }
    if (track.time_of_day !== 'ALL' || track.weather !== 'ALL') {
      throw new Error(`Track '${track.id}' must use truthful untagged defaults ALL/ALL.`);
    }
    if (!Number.isSafeInteger(track.size_bytes) || track.size_bytes <= 0) {
      throw new Error(`Track '${track.id}' has invalid size_bytes.`);
    }

    const parsed = new URL(track.stream_url);
    const expectedBase = new URL(normalizedBase);
    if (
      parsed.protocol !== 'https:' ||
      parsed.hostname.toLowerCase() !== expectedBase.hostname.toLowerCase() ||
      parsed.port ||
      parsed.username ||
      parsed.password ||
      parsed.search ||
      parsed.hash ||
      !parsed.pathname.startsWith('/v1/audio/') ||
      parsed.pathname.length <= '/v1/audio/'.length
    ) {
      throw new Error(`Track '${track.id}' has a URL outside the configured private Worker audio route.`);
    }
  }

  for (const [theme, id] of Object.entries(manifest.theme_defaults ?? {})) {
    if (!THEME_SET.has(theme)) throw new Error(`Unknown theme_defaults key '${theme}'.`);
    const track = manifest.tracks.find((candidate) => candidate.id === id);
    if (!track || track.game_theme !== theme) {
      throw new Error(`theme_defaults.${theme} must point to a real track in the same theme.`);
    }
  }

  for (const theme of THEME_SET) {
    const hasTracks = manifest.tracks.some((track) => track.game_theme === theme);
    if (hasTracks && !manifest.theme_defaults?.[theme]) {
      throw new Error(`Theme ${theme} has tracks but no valid theme default.`);
    }
  }
}
