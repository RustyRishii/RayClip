import http from "node:http";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

loadEnv();

const host = process.env.RAYCLIP_HOST || "0.0.0.0";
const port = Number(process.env.PORT || process.env.RAYCLIP_PORT || 8787);
const token = process.env.RAYCLIP_TOKEN || "";
const ttlSeconds = Number(process.env.RAYCLIP_TTL_SECONDS || 3600);
const maxClipBytes = Number(process.env.RAYCLIP_MAX_CLIP_BYTES || 5242880);
const dataFile = process.env.RAYCLIP_DATA_FILE || "./data/clips.jsonl";
const dataPath = path.resolve(process.cwd(), dataFile);

if (!token || token === "change-me") {
  console.warn("[rayclip] RAYCLIP_TOKEN is not set to a strong value. Set it before using this on a network.");
}

const clips = [];
const sseClients = new Set();

loadPersistedClips();

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

    if (req.method === "GET" && url.pathname === "/health") {
      return sendJson(res, 200, { ok: true, clipCount: clips.length });
    }

    if (!isAuthorized(req)) {
      return sendJson(res, 401, { error: "unauthorized" });
    }

    if (req.method === "POST" && url.pathname === "/v1/clips") {
      return handleCreateClip(req, res);
    }

    if (req.method === "GET" && url.pathname === "/v1/clips/latest") {
      return sendJson(res, 200, { clip: getLatestClip() });
    }

    if (req.method === "GET" && url.pathname === "/v1/events") {
      return handleEvents(req, res);
    }

    sendJson(res, 404, { error: "not_found" });
  } catch (error) {
    console.error("[rayclip] request failed", error);
    sendJson(res, 500, { error: "internal_error" });
  }
});

server.listen(port, host, () => {
  console.log(`[rayclip] server listening at http://${host}:${port}`);
});

async function handleCreateClip(req, res) {
  const rawBody = await readRequestBody(req, maxClipBytes);
  let body;

  try {
    body = JSON.parse(rawBody);
  } catch {
    return sendJson(res, 400, { error: "invalid_json" });
  }

  const text = typeof body.text === "string" ? body.text : "";
  const data = typeof body.data === "string" ? body.data : "";
  const contentType = typeof body.contentType === "string" ? body.contentType : (data ? "image/png" : "text/plain");
  const sourceDeviceId = sanitizeId(body.sourceDeviceId);
  const sourceDeviceName = sanitizeName(body.sourceDeviceName);

  if (!text && !data) {
    return sendJson(res, 400, { error: "empty_content" });
  }

  if (!sourceDeviceId) {
    return sendJson(res, 400, { error: "missing_source_device_id" });
  }

  const now = new Date();
  const contentHash = text ? sha256(text) : sha256(data);
  const clip = {
    id: crypto.randomUUID(),
    text,
    data,
    contentType,
    sourceDeviceId,
    sourceDeviceName,
    sha256: contentHash,
    createdAt: now.toISOString(),
    expiresAt: new Date(now.getTime() + ttlSeconds * 1000).toISOString()
  };

  clips.push(clip);
  pruneClips();
  persistClip(clip);
  broadcastClip(clip);

  sendJson(res, 201, { clip });
}

function handleEvents(req, res) {
  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache, no-transform",
    Connection: "keep-alive",
    "X-Accel-Buffering": "no"
  });

  res.write(": connected\n\n");

  const client = { res };
  sseClients.add(client);

  const keepAlive = setInterval(() => {
    res.write(": keep-alive\n\n");
  }, 25000);

  req.on("close", () => {
    clearInterval(keepAlive);
    sseClients.delete(client);
  });
}

function broadcastClip(clip) {
  const payload = `event: clip\ndata: ${JSON.stringify({ clip })}\n\n`;

  for (const client of sseClients) {
    client.res.write(payload);
  }
}

function getLatestClip() {
  pruneClips();
  return clips.at(-1) || null;
}

function pruneClips() {
  const now = Date.now();

  while (clips.length > 0 && Date.parse(clips[0].expiresAt) <= now) {
    clips.shift();
  }

  while (clips.length > 100) {
    clips.shift();
  }
}

function isAuthorized(req) {
  if (!token) {
    return false;
  }

  const expected = `Bearer ${token}`;
  const actual = req.headers.authorization || "";

  if (Buffer.byteLength(actual) !== Buffer.byteLength(expected)) {
    return false;
  }

  return crypto.timingSafeEqual(Buffer.from(actual), Buffer.from(expected));
}

function sendJson(res, statusCode, body) {
  const payload = JSON.stringify(body);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload)
  });
  res.end(payload);
}

function readRequestBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    let total = 0;
    const chunks = [];

    req.on("data", (chunk) => {
      total += chunk.length;

      if (total > maxBytes) {
        reject(new Error("request_too_large"));
        req.destroy();
        return;
      }

      chunks.push(chunk);
    });

    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

function sanitizeId(value) {
  if (typeof value !== "string") {
    return "";
  }

  return value.trim().replace(/[^a-zA-Z0-9._:-]/g, "").slice(0, 120);
}

function sanitizeName(value) {
  if (typeof value !== "string") {
    return "";
  }

  return value.trim().slice(0, 120);
}

function sha256(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

function persistClip(clip) {
  try {
    fs.mkdirSync(path.dirname(dataPath), { recursive: true });
    fs.appendFileSync(dataPath, `${JSON.stringify(clip)}\n`, "utf8");
  } catch (error) {
    console.warn("[rayclip] failed to persist clip", error.message);
  }
}

function loadPersistedClips() {
  if (!fs.existsSync(dataPath)) {
    return;
  }

  try {
    const rows = fs.readFileSync(dataPath, "utf8").split("\n").filter(Boolean);

    for (const row of rows.slice(-100)) {
      const clip = JSON.parse(row);

      if (clip?.text && clip?.expiresAt && Date.parse(clip.expiresAt) > Date.now()) {
        clips.push(clip);
      }
    }

    pruneClips();
  } catch (error) {
    console.warn("[rayclip] failed to load persisted clips", error.message);
  }
}

function loadEnv() {
  const cwdEnv = path.resolve(process.cwd(), ".env");
  const moduleEnv = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../.env");
  const envPath = fs.existsSync(cwdEnv) ? cwdEnv : moduleEnv;

  if (!fs.existsSync(envPath)) {
    return;
  }

  const rows = fs.readFileSync(envPath, "utf8").split("\n");

  for (const row of rows) {
    const trimmed = row.trim();

    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }

    const separatorIndex = trimmed.indexOf("=");

    if (separatorIndex === -1) {
      continue;
    }

    const key = trimmed.slice(0, separatorIndex).trim();
    const value = trimmed.slice(separatorIndex + 1).trim();

    if (key && process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}
