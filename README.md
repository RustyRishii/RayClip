# RayClip

RayClip is a frictionless, cross-device clipboard sync system designed to seamlessly bridge macOS and modern Android devices (Android 12+). 

By leveraging native macOS Swift APIs and a custom open-source Android Keyboard (IME), RayClip completely bypasses modern background restrictions to deliver near-instant, invisible clipboard syncing without requiring you to open any apps.

## The Architecture

The system consists of three distinct components:

1. **`server`**: A lightweight Node.js hub that stores the latest clip and broadcasts changes instantly using **Server-Sent Events (SSE)**.
2. **`mac-app`**: A native Swift macOS Menu Bar app that monitors `NSPasteboard` and listens to the SSE stream for instant push updates.
3. **`heliboard`**: A custom fork of the open-source Android keyboard. It uses the "Default IME" loophole to achieve true background clipboard polling on Android 12+.

```text
HeliBoard (Android 12+)
  |-- Poll local clipboard every 2.5s
  |-- POST /v1/clips
  |
Sync Server (Node.js)
  |-- Saves latest clip (clips.jsonl)
  |-- Broadcasts via SSE (Push)
  |
RayClip Mac App (Native Swift)
  |-- Instant receive via SSE
  |-- Writes to NSPasteboard
  |-- Polls local clipboard every 1.0s
```

## The Engineering Challenge: Why a Custom Keyboard?

Starting in Android 12, Google implemented strict privacy rules: **No app running in the background is allowed to read the clipboard.** If a standard app tries, the OS silently blocks it.

There is only one exception: The **Default Input Method Editor (IME)**. The operating system inherently trusts your active keyboard. 

By taking the open-source HeliBoard keyboard and injecting our `RayClipSyncManager` directly into its core, we inherit this system-level trust. When you set this custom HeliBoard as your default keyboard, Android grants it permission to monitor and write to the clipboard completely invisibly in the background.

*(Note: HeliBoard uses a specific trick—polling the `ClipDescription` timestamps instead of the actual `ClipData`—to prevent Android 13+ from showing a "HeliBoard pasted from your clipboard" privacy toast every 2.5 seconds).*

## Universal Clipboard: Apple vs. RayClip

Apple's native Universal Clipboard uses Bluetooth Low Energy (BLE) to detect proximity, and then establishes a peer-to-peer Wi-Fi Direct connection to transfer the clipboard exactly when you hit `Cmd+V`. It requires zero polling.

Because we do not control the core Android OS, RayClip relies on a slightly different approach:
* **Mac side:** Uses **Server-Sent Events (SSE)**. When your phone uploads a clip, the server instantly "pushes" it to the Mac app via the open stream. The latency is practically zero.
* **Android side:** Because background streams are heavily restricted and killed by Android battery managers, HeliBoard relies on a highly efficient **2.5-second polling loop**.

## Quick Start

### 1. Start the Sync Server

Deploy the `server` folder to a cloud provider like Render, Railway, or Fly.io (or run it locally on your Mac if you only want it to work on your home Wi-Fi).

```sh
cd ~/projects/RayClip/server
cp .env.example .env
# Edit .env and set a strong RAYCLIP_TOKEN (e.g., openssl rand -hex 32)
npm start
```

### 2. Build and Install HeliBoard

Open `heliboard` in Android Studio, or build it via Gradle:

```sh
cd ~/projects/RayClip/heliboard
./gradlew assembleDebugNoMinify
```

1. Install the APK on your Android device.
2. Go to Android Settings -> Keyboards and **enable** HeliBoard.
3. Set HeliBoard as your **Default Keyboard**.
4. Open the HeliBoard settings app -> **RayClip Sync**.
5. Enter your server URL and token.
6. **Crucial for Cellular Data:** Go to Android App Info for HeliBoard -> Mobile Data -> Enable "Allow background data usage".

### 3. Build and Run the Mac App

The Mac app is completely native, requiring macOS 13+.

```sh
cd ~/projects/RayClip/mac-app
./build.sh
```

This will compile the Swift code into a standalone `RayClip.app` bundle.
1. Double-click `RayClip.app` (You can move it to your `/Applications` folder).
2. Click the Paperclip icon in your Mac's Menu Bar.
3. Enter your Server URL and Token, and enable "Launch at Login".

## Security Notes

This system authenticates the API but stores clipboard text as plaintext on the server. For personal production use, the next step should be end-to-end encryption (E2EE) where only your devices know the encryption key.

Recommended production hardening:
- End-to-end encryption before upload.
- Short clipboard TTL on the server (e.g., 5 minutes).
- Never sync password manager fields.
