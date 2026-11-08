const AUDIO_PREFIX = "/v1/audio/";
const MANIFEST_PATH = "/v1/manifest";
const encoder = new TextEncoder();

function json(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "private, no-store",
      "X-Content-Type-Options": "nosniff",
      ...extraHeaders,
    },
  });
}

function equalSecret(provided, expected) {
  if (typeof provided !== "string" || typeof expected !== "string") return false;
  const left = encoder.encode(provided);
  const right = encoder.encode(expected);
  if (left.length !== right.length) return false;
  let mismatch = 0;
  for (let index = 0; index < left.length; index += 1) mismatch |= left[index] ^ right[index];
  return mismatch === 0;
}

function isAuthorized(request, env) {
  const expected = env.PERSONA_ACCESS_TOKEN;
  if (typeof expected !== "string" || !/^[a-f0-9]{64}$/i.test(expected)) return false;
  const match = /^Bearer ([a-f0-9]{64})$/i.exec(request.headers.get("Authorization") ?? "");
  return match !== null && equalSecret(match[1].toLowerCase(), expected.toLowerCase());
}

function protectedHeaders(object) {
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("ETag", object.httpEtag);
  headers.set("Accept-Ranges", "bytes");
  headers.set("Cache-Control", "private, no-store");
  headers.set("Vary", "Authorization");
  headers.set("X-Content-Type-Options", "nosniff");
  return headers;
}

function audioObjectKey(pathname) {
  if (!pathname.startsWith(AUDIO_PREFIX)) return null;
  const encodedKey = pathname.slice(AUDIO_PREFIX.length);
  if (!encodedKey || encodedKey.length > 900) return null;
  let segments;
  try { segments = encodedKey.split("/").map((segment) => decodeURIComponent(segment)); }
  catch { return null; }
  if (segments.some((segment) => !segment || segment === "." || segment === ".." ||
      segment.includes("\\") || segment.includes("\0") || segment.includes("/"))) return null;
  return `audio/${segments.join("/")}`;
}

async function serveManifest(request, env) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return json({ error: "method_not_allowed" }, 405, { Allow: "GET, HEAD" });
  }
  const metadata = await env.MUSIC_BUCKET.head("manifest.json");
  if (!metadata) return json({ error: "manifest_not_found" }, 404);
  const ifNoneMatch = request.headers.get("If-None-Match");
  if (ifNoneMatch?.split(",").map((tag) => tag.trim()).some((tag) => tag === "*" || tag === metadata.httpEtag)) {
    return new Response(null, { status: 304, headers: {
      ETag: metadata.httpEtag, "Cache-Control": "private, no-store", Vary: "Authorization",
    } });
  }
  const object = request.method === "HEAD" ? metadata : await env.MUSIC_BUCKET.get("manifest.json");
  if (!object) return json({ error: "manifest_not_found" }, 404);
  const headers = protectedHeaders(object);
  headers.set("Content-Type", "application/json; charset=utf-8");
  headers.set("Content-Length", String(object.size));
  return new Response(request.method === "HEAD" ? null : object.body, { status: 200, headers });
}

async function serveAudio(request, env, key) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return json({ error: "method_not_allowed" }, 405, { Allow: "GET, HEAD" });
  }
  if (request.method === "HEAD") {
    const object = await env.MUSIC_BUCKET.head(key);
    if (!object) return json({ error: "audio_not_found" }, 404);
    const headers = protectedHeaders(object);
    headers.set("Content-Length", String(object.size));
    return new Response(null, { status: 200, headers });
  }

  let object;
  try {
    object = await env.MUSIC_BUCKET.get(key, { range: request.headers });
  } catch (error) {
    // R2 error 10039 means an unsatisfiable range. Do not mask unrelated R2 failures as 416.
    if (!(error instanceof Error) || !/\(10039\)\s*$/.test(error.message)) throw error;
    const metadata = await env.MUSIC_BUCKET.head(key);
    if (!metadata) return json({ error: "audio_not_found" }, 404);
    return new Response(null, { status: 416, headers: {
      "Content-Range": `bytes */${metadata.size}`, "Accept-Ranges": "bytes",
      "Cache-Control": "private, no-store", "X-Content-Type-Options": "nosniff",
    } });
  }
  if (!object) return json({ error: "audio_not_found" }, 404);
  const headers = protectedHeaders(object);
  let status = 200;
  if (object.range) {
    const offset = object.range.offset ?? 0;
    const length = object.range.length;
    if (typeof length === "number") {
      headers.set("Content-Range", `bytes ${offset}-${offset + length - 1}/${object.size}`);
      headers.set("Content-Length", String(length));
      status = 206;
    }
  } else {
    headers.set("Content-Length", String(object.size));
  }
  return new Response(object.body, { status, headers });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!env.PERSONA_ACCESS_TOKEN) return json({ error: "worker_not_configured" }, 503);
    if (!isAuthorized(request, env)) {
      return json({ error: "unauthorized" }, 401, {
        "WWW-Authenticate": 'Bearer realm="PersonaMusicPlayer"',
      });
    }
    if (url.pathname === "/v1/health") {
      return request.method === "GET" ? json({ ok: true }) : json({ error: "method_not_allowed" }, 405, { Allow: "GET" });
    }
    if (url.pathname === MANIFEST_PATH) return serveManifest(request, env);
    const key = audioObjectKey(url.pathname);
    if (key) return serveAudio(request, env, key);
    if (url.pathname.startsWith(AUDIO_PREFIX)) return json({ error: "invalid_audio_path" }, 400);
    return json({ error: "not_found" }, 404);
  },
};

export { audioObjectKey, equalSecret, isAuthorized };
