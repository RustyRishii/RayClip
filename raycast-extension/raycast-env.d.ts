/// <reference types="@raycast/api">

/* 🚧 🚧 🚧
 * This file is auto-generated from the extension's manifest.
 * Do not modify manually. Instead, update the `package.json` file.
 * 🚧 🚧 🚧 */

/* eslint-disable @typescript-eslint/ban-types */

type ExtensionPreferences = {
  /** API URL - RayClip server URL, for example https://rayclip.example.com */
  "apiUrl": string,
  /** Token - Bearer token shared with the RayClip server. */
  "token": string,
  /** Device ID - Stable ID for this Mac, for example macbook-air. */
  "deviceId": string,
  /** Device Name - Human-readable device name. */
  "deviceName": string
}

/** Preferences accessible in all the extension's commands */
declare type Preferences = ExtensionPreferences

declare namespace Preferences {
  /** Preferences accessible in the `push-current-clipboard` command */
  export type PushCurrentClipboard = ExtensionPreferences & {}
  /** Preferences accessible in the `pull-latest` command */
  export type PullLatest = ExtensionPreferences & {}
  /** Preferences accessible in the `background-sync` command */
  export type BackgroundSync = ExtensionPreferences & {}
}

declare namespace Arguments {
  /** Arguments passed to the `push-current-clipboard` command */
  export type PushCurrentClipboard = {}
  /** Arguments passed to the `pull-latest` command */
  export type PullLatest = {}
  /** Arguments passed to the `background-sync` command */
  export type BackgroundSync = {}
}

