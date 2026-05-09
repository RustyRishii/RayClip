import { Clipboard, getPreferenceValues, LocalStorage, showToast, Toast } from "@raycast/api";
import { createHash } from "crypto";

type Preferences = {
  apiUrl: string;
  token: string;
  deviceId: string;
  deviceName: string;
};

type Clip = {
  id: string;
  text: string;
  sourceDeviceId: string;
  sourceDeviceName?: string;
  sha256: string;
  createdAt: string;
};

const LAST_LOCAL_HASH_KEY = "rayclip:last-local-hash";
const LAST_REMOTE_CLIP_ID_KEY = "rayclip:last-remote-clip-id";
const LAST_REMOTE_HASH_KEY = "rayclip:last-remote-hash";

export async function pushCurrentClipboard({ quiet = false }: { quiet?: boolean } = {}) {
  const text = await Clipboard.readText();

  if (!text) {
    if (!quiet) {
      await showToast({ style: Toast.Style.Failure, title: "Clipboard is empty" });
    }
    return false;
  }

  const currentHash = sha256(text);
  const lastLocalHash = await LocalStorage.getItem<string>(LAST_LOCAL_HASH_KEY);
  const lastRemoteHash = await LocalStorage.getItem<string>(LAST_REMOTE_HASH_KEY);

  if (currentHash === lastLocalHash || currentHash === lastRemoteHash) {
    return false;
  }

  const preferences = getPreferences();

  const response = await fetch(`${preferences.apiUrl}/v1/clips`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${preferences.token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      text,
      sourceDeviceId: preferences.deviceId,
      sourceDeviceName: preferences.deviceName
    })
  });

  if (!response.ok) {
    throw new Error(`RayClip upload failed with ${response.status}`);
  }

  await LocalStorage.setItem(LAST_LOCAL_HASH_KEY, currentHash);

  if (!quiet) {
    await showToast({ style: Toast.Style.Success, title: "Pushed clipboard to RayClip" });
  }

  return true;
}

export async function pullLatest({ quiet = false }: { quiet?: boolean } = {}) {
  const preferences = getPreferences();

  const response = await fetch(`${preferences.apiUrl}/v1/clips/latest`, {
    headers: {
      Authorization: `Bearer ${preferences.token}`
    }
  });

  if (!response.ok) {
    throw new Error(`RayClip pull failed with ${response.status}`);
  }

  const body = (await response.json()) as { clip: Clip | null };
  const clip = body.clip;

  if (!clip) {
    if (!quiet) {
      await showToast({ style: Toast.Style.Animated, title: "No remote clipboard yet" });
    }
    return false;
  }

  const lastRemoteClipId = await LocalStorage.getItem<string>(LAST_REMOTE_CLIP_ID_KEY);

  if (clip.id === lastRemoteClipId || clip.sourceDeviceId === preferences.deviceId) {
    return false;
  }

  await Clipboard.copy(clip.text);
  await LocalStorage.setItem(LAST_REMOTE_CLIP_ID_KEY, clip.id);
  await LocalStorage.setItem(LAST_REMOTE_HASH_KEY, clip.sha256 || sha256(clip.text));
  await LocalStorage.setItem(LAST_LOCAL_HASH_KEY, clip.sha256 || sha256(clip.text));

  if (!quiet) {
    await showToast({
      style: Toast.Style.Success,
      title: "Copied remote clipboard",
      message: clip.sourceDeviceName || clip.sourceDeviceId
    });
  }

  return true;
}

export async function runBackgroundSync() {
  try {
    await pushCurrentClipboard({ quiet: true });
    await pullLatest({ quiet: true });
  } catch (error) {
    await showToast({
      style: Toast.Style.Failure,
      title: "RayClip sync failed",
      message: error instanceof Error ? error.message : String(error)
    });
  }
}

function getPreferences() {
  const preferences = getPreferenceValues<Preferences>();

  return {
    ...preferences,
    apiUrl: preferences.apiUrl.replace(/\/+$/, "")
  };
}

function sha256(value: string) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}
