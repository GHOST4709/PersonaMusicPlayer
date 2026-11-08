import test from "node:test";
import assert from "node:assert/strict";
import worker, { audioObjectKey, equalSecret, isAuthorized } from "../src/index.js";

const TOKEN = "0123456789abcdef".repeat(4);

function makeObject(body, options = {}) {
  const bytes = Buffer.from(body);
  return {
    size: bytes.length,
    httpEtag: '"etag-123"',
    range: options.range,
    body: options.range
      ? new ReadableStream({ start(controller) {
          controller.enqueue(bytes.subarray(options.range.offset, options.range.offset + options.range.length));
          controller.close();
        } })
      : new ReadableStream({ start(controller) { controller.enqueue(bytes); controller.close(); } }),
    writeHttpMetadata(headers) { headers.set("Content-Type", options.contentType ?? "audio/mpeg"); },
  };
}

function mockEnv({ manifest = '{"version":2}', audio = "0123456789" } = {}) {
  return {
    PERSONA_ACCESS_TOKEN: TOKEN,
    MUSIC_BUCKET: {
      async head(key) {
        if (key === "manifest.json") return { ...makeObject(manifest), body: undefined };
        if (key === "audio/test.mp3") return { ...makeObject(audio), body: undefined };
        return null;
      },
      async get(key, options = {}) {
        if (key === "manifest.json") return makeObject(manifest, { contentType: "application/json" });
        if (key !== "audio/test.mp3") return null;
        const rangeHeader = options.range?.get?.("Range");
        if (rangeHeader) {
          const match = /^bytes=(\d+)-(\d*)$/.exec(rangeHeader);
          if (match) {
            const offset = Number(match[1]);
            const end = match[2] ? Number(match[2]) : audio.length - 1;
            return makeObject(audio, { range: { offset, length: end - offset + 1 } });
          }
        }
        return makeObject(audio);
      },
    },
  };
}

function request(path, { method = "GET", token = TOKEN, headers = {} } = {}) {
  const requestHeaders = new Headers(headers);
  if (token) requestHeaders.set("Authorization", `Bearer ${token}`);
  return new Request(`https://music.example.workers.dev${path}`, { method, headers: requestHeaders });
}

test("secret comparison and bearer verification", () => {
  assert.equal(equalSecret(TOKEN, TOKEN), true);
  assert.equal(equalSecret(`${TOKEN.slice(0, -1)}0`, TOKEN), false);
  assert.equal(isAuthorized(request("/v1/health"), { PERSONA_ACCESS_TOKEN: TOKEN }), true);
  assert.equal(isAuthorized(request("/v1/health", { token: "wrong" }), { PERSONA_ACCESS_TOKEN: TOKEN }), false);
});

test("audio object key rejects traversal and malformed paths", () => {
  assert.equal(audioObjectKey("/v1/audio/p3/night/track.m4a"), "audio/p3/night/track.m4a");
  assert.equal(audioObjectKey("/v1/audio/%2e%2e/secret"), null);
  assert.equal(audioObjectKey("/v1/audio/a%2Fb.mp3"), null);
  assert.equal(audioObjectKey("/v1/audio/"), null);
});

test("all endpoints reject missing private bearer token", async () => {
  const response = await worker.fetch(request("/v1/manifest", { token: "" }), mockEnv());
  assert.equal(response.status, 401);
  assert.equal(response.headers.get("Cache-Control"), "private, no-store");
});

test("misconfigured token secret fails closed", async () => {
  const env = mockEnv(); env.PERSONA_ACCESS_TOKEN = "not-a-64-character-token";
  const response = await worker.fetch(request("/v1/manifest"), env);
  assert.equal(response.status, 401);
});

test("manifest is private and supports ETag revalidation", async () => {
  const env = mockEnv();
  const first = await worker.fetch(request("/v1/manifest"), env);
  assert.equal(first.status, 200);
  assert.equal(first.headers.get("Cache-Control"), "private, no-store");
  assert.equal(first.headers.get("Content-Type"), "application/json; charset=utf-8");
  const second = await worker.fetch(request("/v1/manifest", { headers: { "If-None-Match": '"etag-123"' } }), env);
  assert.equal(second.status, 304);
});

test("audio endpoint streams bytes and supports HTTP Range requests", async () => {
  const env = mockEnv();
  const full = await worker.fetch(request("/v1/audio/test.mp3"), env);
  assert.equal(full.status, 200);
  assert.equal(full.headers.get("Accept-Ranges"), "bytes");
  assert.equal(full.headers.get("Cache-Control"), "private, no-store");
  assert.equal(await full.text(), "0123456789");
  const partial = await worker.fetch(request("/v1/audio/test.mp3", { headers: { Range: "bytes=2-5" } }), env);
  assert.equal(partial.status, 206);
  assert.equal(partial.headers.get("Content-Range"), "bytes 2-5/10");
  assert.equal(partial.headers.get("Content-Length"), "4");
  assert.equal(await partial.text(), "2345");
});

test("HEAD audio request returns headers without a body", async () => {
  const response = await worker.fetch(request("/v1/audio/test.mp3", { method: "HEAD" }), mockEnv());
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("Content-Length"), "10");
  assert.equal(response.headers.get("Accept-Ranges"), "bytes");
  assert.equal(await response.text(), "");
});

test("only GET and HEAD are allowed for media", async () => {
  const response = await worker.fetch(request("/v1/audio/test.mp3", { method: "PUT" }), mockEnv());
  assert.equal(response.status, 405);
});

test("unsatisfiable range produces 416 and object length", async () => {
  const env = mockEnv();
  env.MUSIC_BUCKET.get = async (key) => {
    if (key === "audio/test.mp3") throw new Error("get: The requested range is not satisfiable (10039)");
    return null;
  };
  const response = await worker.fetch(request("/v1/audio/test.mp3", { headers: { Range: "bytes=50-60" } }), env);
  assert.equal(response.status, 416);
  assert.equal(response.headers.get("Content-Range"), "bytes */10");
});

test("R2 service errors are not disguised as range errors", async () => {
  const env = mockEnv();
  env.MUSIC_BUCKET.get = async () => { throw new Error("get: internal error (10001)"); };
  await assert.rejects(worker.fetch(request("/v1/audio/test.mp3", { headers: { Range: "bytes=0-1" } }), env), /internal error/);
});

test("missing audio objects return a generic 404", async () => {
  const response = await worker.fetch(request("/v1/audio/missing.mp3"), mockEnv());
  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), { error: "audio_not_found" });
});
