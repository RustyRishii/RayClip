# RayClip

RayClip is a personal cross-device clipboard sync prototype for Android 12 and macOS.

It is built around one practical constraint: a normal Android background app cannot silently monitor clipboard changes. The Android client therefore includes an input method service (keyboard/IME). Making RayClip the active/default keyboard gives the app the clipboard access Android reserves for focused apps and the default IME.

## What This Builds

- `server`: a small authenticated Node.js sync API with Server-Sent Events.
- `mac-agent`: a continuously running macOS clipboard bridge using `pbpaste` and `pbcopy`.
- `raycast-extension`: Raycast commands for pushing, pulling, and 10-second background sync.
- `android`: an Android client skeleton with a foreground sync service and IME service.

## Architecture

```text
Android RayClip IME/service
  -> POST /v1/clips
  -> RayClip server
  -> SSE / polling
  -> mac-agent or Raycast
  -> macOS clipboard

mac-agent or Raycast
  -> POST /v1/clips
  -> RayClip server
  -> Android polling
  -> Android clipboard
```

## MVP Status

This repo is intentionally dependency-light so the core can run immediately:

- The server and macOS agent use only built-in Node.js modules.
- The Raycast extension uses Raycast's normal extension tooling.
- The Android project is a starter Android Studio project. It uses Java and plain Android APIs.

## Quick Start

Use one shared token everywhere.

```sh
openssl rand -hex 32
```

### 1. Start the sync server

```sh
cd ~/projects/RayClip/server
cp .env.example .env
# edit .env and set a strong RAYCLIP_TOKEN
npm start
```

For physical Android testing on the same Wi-Fi:

- Keep `RAYCLIP_HOST=0.0.0.0` in `server/.env`.
- Use your Mac LAN IP in Android, for example `http://192.168.1.10:8787`.
- You can find your Mac IP with:

```sh
ipconfig getifaddr en0
```

The server is still reachable locally from Mac at `http://127.0.0.1:8787`.

For real Android-to-Mac sync outside the same Wi-Fi network, deploy this server somewhere with HTTPS and set the same public URL in the Android app and macOS tools.

### 2. Start the macOS clipboard agent

```sh
cd ~/projects/RayClip/mac-agent
cp .env.example .env
# edit .env and set RAYCLIP_API_URL and RAYCLIP_TOKEN
npm start
```

This is the most reliable macOS side because it runs continuously. Raycast background commands are limited to scheduled intervals.

### 3. Run the Raycast extension

```sh
cd ~/projects/RayClip/raycast-extension
npm install
npm run dev
```

Then configure the extension preferences:

- API URL: your server URL
- Token: the same `RAYCLIP_TOKEN`
- Device ID: something stable like `macbook-air`
- Device name: something human-readable like `Rishi MacBook Air`

### 4. Build the Android client

Open `~/projects/RayClip/android` in Android Studio.

Run the app on your Android 12 device, then:

1. Open the RayClip app.
2. Enter your server URL and token.
3. Tap `Test Server Connection` and confirm success.
4. Tap `Start Sync Service`.
5. Enable RayClip as an input method in Android settings.
6. Select RayClip as the current keyboard.

The IME route is the important part. Without it, Android may block clipboard reads while the app is in the background.

## API

### `POST /v1/clips`

```json
{
  "text": "hello",
  "sourceDeviceId": "android-phone",
  "sourceDeviceName": "Pixel"
}
```

### `GET /v1/clips/latest`

Returns the newest non-expired clip.

### `GET /v1/events`

Server-Sent Events stream. The macOS agent uses this for near-instant updates.

All endpoints except `/health` require:

```text
Authorization: Bearer <RAYCLIP_TOKEN>
```

## Security Notes

This MVP authenticates the API but stores clipboard text as plaintext on the server. For personal production use, the next step should be end-to-end encryption where only your devices know the encryption key.

Recommended production hardening:

- End-to-end encryption before upload.
- Short clipboard TTL, for example 5 to 30 minutes.
- A pause/sensitive-mode toggle.
- Never sync password manager fields.
- Device pairing instead of manually sharing one long token.

## Known Limitations

- Android clipboard monitoring requires the RayClip IME/default keyboard workaround.
- Raycast background refresh is interval-based, not truly instant.
- Android background service behavior can vary by OEM battery settings.
- Images/files are not implemented yet. This MVP syncs text.
