#!/usr/bin/env node
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import readline from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import {
  GetObjectCommand,
  HeadObjectCommand,
  ListObjectsV2Command,
  PutObjectCommand,
  S3Client,
} from '@aws-sdk/client-s3';
import { buildManifest, listAllObjects, PERSONA_FOLDERS } from './manifest-core.mjs';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptDir, '../..');
const defaultOutputPath = path.join(scriptDir, 'output', 'manifest.json');
const defaultDefaultsPath = path.join(scriptDir, 'theme-defaults.json');

function parseArgs(argv) {
  const options = {
    upload: false,
    output: defaultOutputPath,
    defaults: defaultDefaultsPath,
    bucket: process.env.R2_BUCKET || 'persona-music',
  };

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--upload') options.upload = true;
    else if (argument === '--output') options.output = argv[++index];
    else if (argument.startsWith('--output=')) options.output = argument.slice('--output='.length);
    else if (argument === '--defaults') options.defaults = argv[++index];
    else if (argument.startsWith('--defaults=')) options.defaults = argument.slice('--defaults='.length);
    else if (argument === '--bucket') options.bucket = argv[++index];
    else if (argument.startsWith('--bucket=')) options.bucket = argument.slice('--bucket='.length);
    else if (argument === '--help' || argument === '-h') options.help = true;
    else throw new Error(`Unknown argument: ${argument}`);
  }

  if (!options.output || !options.bucket) throw new Error('--output and --bucket require values.');
  return options;
}

function parseProperties(text) {
  const properties = {};
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) continue;
    const separator = trimmed.indexOf('=');
    if (separator <= 0) continue;
    const key = trimmed.slice(0, separator).trim();
    const value = trimmed.slice(separator + 1).trim();
    properties[key] = value;
  }
  return properties;
}

async function getWorkerBaseUrl() {
  const fromEnvironment = process.env.MUSIC_API_BASE_URL?.trim();
  if (fromEnvironment) return fromEnvironment;
  const localPropertiesPath = path.join(repositoryRoot, 'local.properties');
  try {
    const properties = parseProperties(await readFile(localPropertiesPath, 'utf8'));
    return properties.MUSIC_API_BASE_URL;
  } catch (error) {
    if (error?.code === 'ENOENT') return undefined;
    throw error;
  }
}

async function getThemeDefaults(filePath) {
  try {
    const text = await readFile(filePath, 'utf8');
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('Theme defaults file must be a JSON object keyed by Persona theme.');
    }
    for (const [theme, value] of Object.entries(parsed)) {
      if (!PERSONA_FOLDERS.some((entry) => entry.theme === theme)) {
        throw new Error(`Unknown Persona theme in defaults file: ${theme}`);
      }
      if (value !== null && typeof value !== 'string') {
        throw new Error(`Default for ${theme} must be an exact R2 object key string or null.`);
      }
    }
    return parsed;
  } catch (error) {
    if (error?.code === 'ENOENT') return {};
    throw error;
  }
}

function requireR2Credentials() {
  const accountId = process.env.R2_ACCOUNT_ID?.trim();
  const accessKeyId = process.env.R2_ACCESS_KEY_ID?.trim();
  const secretAccessKey = process.env.R2_SECRET_ACCESS_KEY;

  const missing = [];
  if (!accountId) missing.push('R2_ACCOUNT_ID');
  if (!accessKeyId) missing.push('R2_ACCESS_KEY_ID');
  if (!secretAccessKey) missing.push('R2_SECRET_ACCESS_KEY');
  if (missing.length) {
    throw new Error(`Missing R2 S3 credentials: ${missing.join(', ')}. Use run.ps1 to enter them privately.`);
  }
  if (!/^[a-f0-9]{32}$/i.test(accountId)) {
    throw new Error('R2_ACCOUNT_ID must be the 32-character Cloudflare account ID shown in the R2 S3 endpoint.');
  }
  return { accountId, accessKeyId, secretAccessKey };
}

function createS3Client({ accountId, accessKeyId, secretAccessKey }) {
  return new S3Client({
    region: 'auto',
    endpoint: `https://${accountId}.r2.cloudflarestorage.com`,
    credentials: { accessKeyId, secretAccessKey },
    maxAttempts: 5,
  });
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(unit === 0 ? 0 : 2)} ${units[unit]}`;
}

function printReport(report, manifest, outputPath) {
  const totalBytes = manifest.tracks.reduce((sum, track) => sum + track.size_bytes, 0);
  console.log('\nR2 catalogue scan');
  console.log('-----------------');
  for (const themeConfig of PERSONA_FOLDERS) {
    const theme = themeConfig.theme;
    const skipped = Object.entries(report.skippedByTheme[theme] ?? {})
      .map(([reason, count]) => `${reason}=${count}`)
      .join(', ') || 'none';
    console.log(`${theme} (${themeConfig.prefix}): ${report.includedByTheme[theme]}/${report.rawObjects[theme]} included; skipped: ${skipped}`);
  }
  console.log(`Total tracks: ${manifest.tracks.length}`);
  console.log(`Total listed audio size: ${formatBytes(totalBytes)} (metadata only; no audio downloaded)`);
  console.log(`Duplicate display titles: ${report.titleCollisions.length}`);
  console.log(`Stable ID collisions: ${report.idCollisions.length}`);
  console.log('\nTheme defaults:');
  for (const [theme, selection] of Object.entries(report.selectedDefaults)) {
    console.log(`  ${theme}: ${selection.title} [${selection.selection}]`);
    console.log(`    R2 key: ${selection.object_key}`);
    if (selection.selection === 'first_sorted_object') {
      console.log('    Review this provisional default; see theme-defaults.example.json to override it.');
    }
  }
  if (report.titleCollisions.length) {
    console.warn('\nDuplicate display-title collisions (allowed; IDs remain distinct):');
    for (const collision of report.titleCollisions) {
      console.warn(`  ${collision.theme} / ${collision.title}: ${collision.keys.join(' | ')}`);
    }
  }
  if (report.skippedSamples.length) {
    console.log('\nSkipped object samples (up to 20):');
    for (const item of report.skippedSamples) {
      console.log(`  ${item.theme}: ${item.key} (${item.reason})`);
    }
  }
  if (report.emptyThemes.length) {
    console.log(`\nPersona folders with no supported tracks yet (allowed): ${report.emptyThemes.join(', ')}`);
    console.log('Their theme_defaults entries are omitted until songs are uploaded.');
  }
  console.log(`\nLocal manifest: ${outputPath}`);
}

async function confirmUpload(bucket) {
  if (!stdin.isTTY || !stdout.isTTY) {
    throw new Error('Upload confirmation requires an interactive terminal. Re-run from PowerShell.');
  }
  const rl = readline.createInterface({ input: stdin, output: stdout });
  try {
    const answer = await rl.question(`\nThis overwrites the private R2 object ${bucket}/manifest.json. Type UPLOAD ${bucket}/manifest.json to continue: `);
    return answer.trim() === `UPLOAD ${bucket}/manifest.json`;
  } finally {
    rl.close();
  }
}

async function uploadAndVerify(client, bucket, manifestBytes) {
  await client.send(new PutObjectCommand({
    Bucket: bucket,
    Key: 'manifest.json',
    Body: manifestBytes,
    ContentType: 'application/json; charset=utf-8',
    CacheControl: 'private, no-store',
    // Deliberately no ACL and no bucket-public-access changes. R2 stays private.
  }));

  const head = await client.send(new HeadObjectCommand({ Bucket: bucket, Key: 'manifest.json' }));
  if (head.ContentLength !== manifestBytes.length) {
    throw new Error(`R2 manifest verification failed: expected ${manifestBytes.length} bytes, got ${head.ContentLength}.`);
  }

  const downloaded = await client.send(new GetObjectCommand({ Bucket: bucket, Key: 'manifest.json' }));
  const body = Buffer.from(await downloaded.Body.transformToByteArray());
  if (!body.equals(manifestBytes)) {
    throw new Error('R2 manifest verification failed: uploaded and downloaded bytes differ.');
  }

  return { contentLength: head.ContentLength, contentType: head.ContentType, cacheControl: head.CacheControl };
}

function printHelp() {
  console.log(`Persona BGM R2 manifest generator

Usage:
  node cloudflare/catalogue/generate-manifest.mjs [--output PATH] [--defaults PATH] [--bucket NAME] [--upload]

Default behaviour is a local dry run. --upload is required to overwrite R2 manifest.json and asks for a typed confirmation.

Configuration:
  MUSIC_API_BASE_URL        HTTPS Worker origin; read from root local.properties if omitted
  R2_ACCOUNT_ID             Cloudflare account ID
  R2_ACCESS_KEY_ID          Bucket-scoped R2 S3 access key ID
  R2_SECRET_ACCESS_KEY      Bucket-scoped R2 S3 secret access key
  R2_BUCKET                 Optional bucket name (default: persona-music)

The Worker bearer token is not used by this generator.`);
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const workerBaseUrl = await getWorkerBaseUrl();
  if (!workerBaseUrl) {
    throw new Error('MUSIC_API_BASE_URL was not found in root local.properties or the environment.');
  }

  const defaultsPath = path.resolve(repositoryRoot, options.defaults);
  const themeDefaults = await getThemeDefaults(defaultsPath);
  const outputPath = path.resolve(repositoryRoot, options.output);

  console.log(`Scanning bucket '${options.bucket}' under audio/P3/, audio/P4/, audio/P5/ ...`);
  const client = createS3Client(requireR2Credentials());
  try {
    const objectsByTheme = {};
    for (const themeConfig of PERSONA_FOLDERS) {
      objectsByTheme[themeConfig.theme] = await listAllObjects(client, options.bucket, themeConfig.prefix, ListObjectsV2Command);
    }

    const { manifest, report } = buildManifest({ objectsByTheme, workerBaseUrl, themeDefaults });
    const manifestBytes = Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
    await mkdir(path.dirname(outputPath), { recursive: true });
    await writeFile(outputPath, manifestBytes);
    printReport(report, manifest, outputPath);

    if (!options.upload) {
      console.log('\nDry run only: R2 was not changed. Review the local manifest before using --upload.');
      return;
    }

    if (report.idCollisions.length) {
      throw new Error('Upload blocked: stable track ID collisions were reported.');
    }

    const approved = await confirmUpload(options.bucket);
    if (!approved) {
      console.log('Upload cancelled. The local manifest was kept; R2 was not changed.');
      return;
    }

    const verification = await uploadAndVerify(client, options.bucket, manifestBytes);
    console.log('\nR2 manifest upload and read-back verification succeeded.');
    console.log(`  Key: manifest.json`);
    console.log(`  Bytes: ${verification.contentLength}`);
    console.log(`  Content-Type: ${verification.contentType ?? '(not returned)'}`);
    console.log(`  Cache-Control: ${verification.cacheControl ?? '(not returned)'}`);
    console.log('  Bucket public-access settings were not changed.');
  } finally {
    client.destroy();
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(`\nManifest generation failed: ${error?.message ?? error}`);
    process.exitCode = 1;
  });
}
