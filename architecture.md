# RayClip Architecture

This document outlines the high-level architecture and data flow of the RayClip cross-device clipboard sync project.

## Architecture Diagram

```mermaid
graph TD
    subgraph Mac [Mac (macOS)]
        agent[mac-agent<br/>Node.js Background Process]
        pb[macOS Clipboard<br/>pbcopy / pbpaste]
        
        agent <-->|Polls every 1s| pb
    end

    subgraph Server_Env [Sync Server Hub (Local or Cloud)]
        server[Node.js HTTP API<br/>Port 8787]
        db[(clips.jsonl<br/>Disk Persistence)]
        
        server -->|Saves state| db
    end

    subgraph Android_Env [Android Device]
        ime[HeliBoard Custom Keyboard<br/>Default IME]
        acl[Android ClipboardManager]

        ime <-->|Reads/Writes & Polls every 2.5s| acl
    end

    %% Network Connections
    agent == "1. POST /v1/clips\n2. SSE Live Stream (/v1/events)" === server
    ime == "1. POST /v1/clips\n2. Polls /v1/clips/latest (every 2.5s)" === server

    classDef mac fill:#e0f2fe,stroke:#0284c7,stroke-width:2px,color:#000;
    classDef server fill:#fef08a,stroke:#ca8a04,stroke-width:2px,color:#000;
    classDef android fill:#dcfce7,stroke:#16a34a,stroke-width:2px,color:#000;
    
    class Mac mac;
    class Server_Env server;
    class Android_Env android;
```

## Component Breakdown

### 1. Sync Server (`/server`)
- **Role:** The central source of truth. It receives new clipboard text from devices, stores it in memory (and backs it up to `clips.jsonl`), and broadcasts it to other connected devices.
- **Communication:** Provides standard HTTP endpoints (`POST /v1/clips`, `GET /v1/clips/latest`) and a Server-Sent Events (SSE) stream (`/v1/events`) for instant push notifications.
- **Hosting:** Can be run locally on your Mac, or hosted in the cloud (e.g., Render.com or Fly.io) so devices don't need to be on the same Wi-Fi.

### 2. Mac Agent (`/mac-agent`)
- **Role:** Bridges your Mac's physical clipboard with the Sync Server.
- **How it reads:** Uses the macOS `pbpaste` command every 1 second to detect if you copied something new. If so, it POSTs it to the server.
- **How it writes:** Maintains a permanent SSE connection to the server. The moment the server receives a clip from your phone, it pushes it to the `mac-agent`, which instantly writes it to your Mac using `pbcopy`.

### 3. HeliBoard Keyboard (`/heliboard`)
- **Role:** Bridges your Android's physical clipboard with the Sync Server. By integrating directly into a professional open-source keyboard (HeliBoard) and setting it as your default IME, we bypass Android 12+ background clipboard restrictions completely.
- **How it reads:** Hooks into the clipboard change event. Even when closed, it polls the clipboard description timestamp to detect copies made in other apps without triggering privacy toasts, instantly POSTing new clips to the server.
- **How it writes:** Polls the server's `/v1/clips/latest` endpoint every 2.5 seconds to pull new clips from the Mac and seamlessly write them to the Android clipboard.
