# RayClip Android

This Android client is a personal-tool skeleton for Android 12+.

It includes:

- A settings activity for API URL/token/device ID.
- A foreground sync service that uploads local clipboard changes and polls for remote clips.
- An input method service (IME) that helps satisfy Android's clipboard access restrictions.

## Important

Android does not allow a normal background app to freely monitor clipboard changes. For automatic Android-to-Mac sync, enable RayClip as an input method and select it as the current keyboard.

This first version provides only a tiny keyboard surface, not a full Gboard replacement. It is enough to prove the clipboard sync path, but the next real usability step is either:

- building a usable keyboard, or
- accepting a manual/share-sheet/notification workflow, or
- using advanced personal-device approaches such as Shizuku/ADB/root.

## Run

1. Open this folder in Android Studio.
2. Build and install `app`.
3. Open RayClip.
4. Set your RayClip server URL and token.
5. Tap `Test Server Connection` and confirm it succeeds.
6. Tap `Start Sync Service`.
7. Enable RayClip in Android keyboard settings.
8. Switch your current keyboard to RayClip.

For local emulator testing against a Mac server:

```text
http://10.0.2.2:8787
```

For a physical Android phone on the same Wi-Fi as your Mac:

- Run the server with `RAYCLIP_HOST=0.0.0.0`.
- Use your Mac LAN URL in the app, for example `http://192.168.1.10:8787`.

For internet access outside local Wi-Fi, deploy HTTPS or expose your Mac temporarily with a tunnel.
