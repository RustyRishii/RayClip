# RayClip

RayClip is a personal cross-device clipboard sync prototype for Android 12 and macOS.

It is built around one practical constraint: a normal Android background app cannot silently monitor clipboard changes. The Android client therefore includes an input method service (keyboard/IME). Making RayClip the active/default keyboard gives the app the clipboard access Android reserves for focused apps and the default IME.

## What This Builds

- `server`: a small authenticated Node.js sync API with Server-Sent Events.
- `mac-agent`: a continuously running macOS clipboard bridge using `pbpaste` and `pbcopy`.
- `heliboard`: a fork of the open-source HeliBoard Android keyboard, with RayClip sync baked directly into its clipboard manager.

## Architecture

```text
HeliBoard (Android Keyboard)
  -> POST /v1/clips
  -> RayClip server
  -> SSE stream
  -> mac-agent
  -> macOS clipboard

mac-agent
  -> POST /v1/clips
  -> RayClip server
  -> Android polling (every 2.5s)
  -> Android clipboard
```

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

This runs continuously in the background using `pbcopy` and `pbpaste`.

### 3. Build and install HeliBoard

Open `~/projects/RayClip/heliboard` in Android Studio, or build it via Gradle:

```sh
cd ~/projects/RayClip/heliboard
./gradlew assembleDebugNoMinify
```

Install the resulting APK on your Android 12+ device, then:

1. Go to your Android settings and enable HeliBoard in your Keyboard list.
2. Set HeliBoard as your default keyboard.
3. Open the HeliBoard settings app.
4. Go to **RayClip Sync** (between Advanced and About).
5. Enter your server URL and token, and tap Save.

By operating as the default IME, HeliBoard is permitted to read and write to the Android clipboard in the background without Android 12+ blocking it.

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
- Android background service behavior can vary by OEM battery settings.
- Images/files are not implemented yet. This MVP syncs text.
