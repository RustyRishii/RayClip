import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execFile, spawn } from "node:child_process";

loadEnv();

const apiUrl = stripTrailingSlash(process.env.RAYCLIP_API_URL || "http://127.0.0.1:8787");
const token = process.env.RAYCLIP_TOKEN || "";
const deviceId = process.env.RAYCLIP_DEVICE_ID || "macbook-air";
const deviceName = process.env.RAYCLIP_DEVICE_NAME || "MacBook";
const pollMs = Number(process.env.RAYCLIP_POLL_MS || 1000);

let lastLocalHash = "";
let lastAppliedRemoteClipId = "";
let lastAppliedRemoteHash = "";
let latestSeenClipId = "";
let sseAbortController = null;

if (!token || token === "change-me") {
  console.warn("[rayclip] Set RAYCLIP_TOKEN in mac-agent/.env before syncing across devices.");
}

console.log(`[rayclip] mac-agent starting as ${deviceId}`);
console.log(`[rayclip] API ${apiUrl}`);

setInterval(syncLocalClipboard, pollMs);
setInterval(fetchLatestClip, 10000);

syncLocalClipboard();
fetchLatestClip();
connectEvents();

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

async function syncLocalClipboard() {
  try {
    const text = await readMacClipboard();

    if (!text) {
      return;
    }

    const currentHash = sha256(text);

    if (currentHash === lastLocalHash || currentHash === lastAppliedRemoteHash) {
      return;
    }

    lastLocalHash = currentHash;

    await postClip(text);
    console.log(`[rayclip] uploaded local clipboard (${text.length} chars)`);
  } catch (error) {
    console.warn("[rayclip] local clipboard sync failed", error.message);
  }
}

async function postClip(text) {
  const response = await fetch(`${apiUrl}/v1/clips`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      text,
      sourceDeviceId: deviceId,
      sourceDeviceName: deviceName
    })
  });

  if (!response.ok) {
    throw new Error(`POST /v1/clips failed: ${response.status}`);
  }

  const body = await response.json();
  latestSeenClipId = body.clip?.id || latestSeenClipId;
}

async function fetchLatestClip() {
  try {
    const response = await fetch(`${apiUrl}/v1/clips/latest`, {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });

    if (!response.ok) {
      throw new Error(`GET /v1/clips/latest failed: ${response.status}`);
    }

    const body = await response.json();
    await applyRemoteClip(body.clip);
  } catch (error) {
    console.warn("[rayclip] latest clip polling failed", error.message);
  }
}

async function connectEvents() {
  while (true) {
    sseAbortController = new AbortController();

    try {
      const response = await fetch(`${apiUrl}/v1/events`, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "text/event-stream"
        },
        signal: sseAbortController.signal
      });

      if (!response.ok) {
        throw new Error(`GET /v1/events failed: ${response.status}`);
      }

      console.log("[rayclip] connected to event stream");
      await readEventStream(response.body);
    } catch (error) {
      if (error.name !== "AbortError") {
        console.warn("[rayclip] event stream disconnected", error.message);
      }
    }

    await sleep(2000);
  }
}

async function readEventStream(stream) {
  const decoder = new TextDecoder();
  const reader = stream.getReader();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();

    if (done) {
      return;
    }

    buffer += decoder.decode(value, { stream: true });

    let boundaryIndex;
    while ((boundaryIndex = buffer.indexOf("\n\n")) !== -1) {
      const rawEvent = buffer.slice(0, boundaryIndex);
      buffer = buffer.slice(boundaryIndex + 2);
      await handleSseEvent(rawEvent);
    }
  }
}

async function handleSseEvent(rawEvent) {
  const dataRows = rawEvent
    .split("\n")
    .filter((row) => row.startsWith("data:"))
    .map((row) => row.slice(5).trim());

  if (dataRows.length === 0) {
    return;
  }

  try {
    const payload = JSON.parse(dataRows.join("\n"));
    await applyRemoteClip(payload.clip);
  } catch (error) {
    console.warn("[rayclip] invalid SSE payload", error.message);
  }
}

async function applyRemoteClip(clip) {
  if (!clip || !clip.text || !clip.id) {
    return;
  }

  if (clip.id === latestSeenClipId || clip.id === lastAppliedRemoteClipId) {
    return;
  }

  latestSeenClipId = clip.id;

  if (clip.sourceDeviceId === deviceId) {
    return;
  }

  await writeMacClipboard(clip.text);
  lastAppliedRemoteClipId = clip.id;
  lastAppliedRemoteHash = sha256(clip.text);
  lastLocalHash = lastAppliedRemoteHash;

  console.log(`[rayclip] applied remote clip from ${clip.sourceDeviceName || clip.sourceDeviceId} (${clip.text.length} chars)`);
}

function readMacClipboard() {
  return new Promise((resolve, reject) => {
    execFile("pbpaste", [], { maxBuffer: 1024 * 1024 }, (error, stdout) => {
      if (error) {
        reject(error);
        return;
      }

      resolve(stdout);
    });
  });
}

function writeMacClipboard(text) {
  return new Promise((resolve, reject) => {
    const child = spawn("pbcopy");

    child.on("error", reject);
    child.on("close", (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`pbcopy exited with ${code}`));
      }
    });

    child.stdin.end(text);
  });
}

function sha256(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

function stripTrailingSlash(value) {
  return value.replace(/\/+$/, "");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function shutdown() {
  sseAbortController?.abort();
  process.exit(0);
}

function loadEnv() {
  const envPath = path.resolve(process.cwd(), ".env");

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
