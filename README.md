# J.A.R.V.I.S — Autonomous Hybrid AI & Phone-Use Assistant for Android (v1.0.2)

<p align="center">
  <strong>Autonomous Phone Use • Screen Perception • Model Context Protocol (MCP) • Shizuku &amp; Wireless ADB • On-Device LiteRT-LM</strong>
</p>

<p align="center">
  <a href="https://github.com/zakmijo2-dotcom/Jarvis-/releases/latest">
    <img src="https://img.shields.io/badge/Version-v1.0.2-0E1015?style=for-the-badge" alt="Version v1.0.2" />
  </a>
  <a href="https://github.com/zakmijo2-dotcom/Jarvis-/releases/latest">
    <img src="https://img.shields.io/badge/Release-APK%20Available-38BDF8?style=for-the-badge&logo=android&logoColor=white" alt="APK Available" />
  </a>
  <img src="https://img.shields.io/badge/License-GPL--3.0-38BDF8?style=for-the-badge" alt="GPL-3.0 License" />
  <img src="https://img.shields.io/badge/Platform-Android%20(SDK%2024--36)-2DD4BF?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK 24-36" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.2.10" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-60A5FA?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/Protocol-MCP%20Client-818CF8?style=for-the-badge" alt="Model Context Protocol" />
  <img src="https://img.shields.io/badge/Bridge-Shizuku%20%2B%20Wireless%20ADB-F59E0B?style=for-the-badge" alt="Shizuku and Wireless ADB" />
  <img src="https://img.shields.io/badge/Local%20AI-LiteRT--LM%200.10.2-2DD4BF?style=for-the-badge" alt="LiteRT-LM 0.10.2" />
</p>

---

## 📥 Download Ready-to-Install APK (v1.0.2)

The latest signed APK packages are automatically compiled and published through GitHub Actions CI/CD:

| Package | Direct Download Link | Description |
|---|---|---|
| **Release APK** | [**Jarvis-release.apk**](https://github.com/zakmijo2-dotcom/Jarvis-/releases/download/v1.0.2/Jarvis-release.apk) | Production signed APK with fast packaging |
| **Debug APK** | [**Jarvis-debug.apk**](https://github.com/zakmijo2-dotcom/Jarvis-/releases/download/v1.0.2/Jarvis-debug.apk) | Developer debug build with active logging & diagnostics |
| **Checksums** | [**SHA256SUMS.txt**](https://github.com/zakmijo2-dotcom/Jarvis-/releases/download/v1.0.2/SHA256SUMS.txt) | Official SHA-256 integrity verification hashes |

> Full release history and changelogs are available on the [**GitHub Releases Page**](https://github.com/zakmijo2-dotcom/Jarvis-/releases).

---

## Overview

**J.A.R.V.I.S** (`v1.0.2`, `com.aistudio.jarvis.k8v4mz`) is an open-source (`GPL-3.0`) native Android AI assistant built in **Kotlin** and **Jetpack Compose (Material 3)**. It integrates **Autonomous Phone Use**, **Real-Time Screen Perception**, **Model Context Protocol (MCP)**, **Shizuku + Wireless ADB** privileged automation, **On-Device LiteRT-LM** inference, and multimodal voice interaction into a secure, privacy-first mobile architecture.

### Core Design Principles
- **Autonomous Phone Use & Visual Verification:** Executes multi-step UI automation (tap, swipe, scroll, type, drag) guided by real-time screen capture, on-device OCR, and accessibility node hierarchy analysis.
- **Model Context Protocol (MCP) Extensibility:** Native MCP client supporting JSON-RPC tool discovery and execution across external agent servers.
- **Privileged Non-Root Control (Shizuku & Wireless ADB):** Deep Android system control via official Shizuku (`13.1.5`) user service and pure-Kotlin Wireless ADB (`kadb:1.3.0`) without requiring device rooting.
- **Local-First & Hardware-Aware AI:** On-device `.litertlm` inference (GPU OpenCL / CPU XNNPACK), Room v7 SQLite persistence (`WRITE_AHEAD_LOGGING`), and dynamic context compression based on thermal and memory headroom.
- **Strict Zero-Trust Privacy Safeguards:** Active application blocklists, automatic password and sensitive field redaction, execution overlay with emergency stop, and Android Keystore AES-256-GCM encryption.

---

## Key Capabilities

### 1. Autonomous Phone Use & Screen Perception (`core/tools/`, `core/vision/`)
- **Phone Use Tools (`PhoneUseTools`):** High-level autonomous device interaction including coordinate tapping, text typing, directional scrolling, gestures, and UI element targeting.
- **MediaProjection Screen Perception (`MediaProjectionCaptureController`):** On-demand foreground screen capture for visual reasoning and OCR-assisted UI navigation.
- **On-Device OCR & Tree Compression (`OnDeviceOcrEngine`, `ScreenTreeCompressor`):** Fast local text extraction and compact accessibility tree serialization within token budgets.
- **Execution Overlay (`ExecutionOverlayManager`):** Floating visual indicator displayed during autonomous execution with an instant user override / emergency cancel button.

### 2. Model Context Protocol (MCP) Integration (`core/mcp/`)
- **Native MCP Client (`McpClient`, `McpManager`):** Connects to remote or local MCP servers over SSE / HTTP transports using standard JSON-RPC 2.0.
- **Dynamic Tool Schema Registration:** Inspects remote server tool registries and seamlessly injects external tools into the J.A.R.V.I.S agent execution loop.

### 3. Shizuku-First & Wireless ADB Device Control (`core/device/`, `aidl/`)
- **Unified Command Router (`DeviceCommandRouter`):** Intelligently dispatches commands across Shizuku (`13.1.5`) → Wireless ADB (`kadb:1.3.0`) → Native APIs → Accessibility fallbacks.
- **Shizuku User Service (`IJarvisShizukuService.aidl`):** Runs privileged system operations inside an isolated user service with death-recipient lifecycle management.
- **Wireless ADB Setup Wizard (`DeviceBridgeSetupScreen`):** Guided Android 11+ pairing with TLS 1.3, mDNS auto-discovery, and automatic pairing code sanitization.

### 4. Local AI Inference & Model Hub (`core/ai/`, `core/model/`, `core/hf/`)
- **LiteRT-LM Engine (`LiteRtLmAiEngine`):** Optimized `.litertlm` execution with GPU OpenCL acceleration, thermal token throttling, and instant coroutine cancellation.
- **Intelligent Prompt Compressor (`PromptCompressor`):** Summarizes conversation history into dense factual digests to respect model context window limits.
- **Hugging Face Model Hub:** Discovers, downloads, and validates `.litertlm` model weights with resumable downloads and SHA-256 verification.

### 5. Multilingual Voice & Web Research (`core/voice/`, `core/web/`, `core/cloud/`)
- **Speech Pipeline (`AndroidVoiceEngine`, `ModularWakeWordEngine`):** Real-time `SpeechRecognizer` + `TextToSpeech` (sanitized against prompt leaks) with optional Picovoice Porcupine wake-word standby.
- **Safe Web Page Extractor (`WebPageExtractor`, `PublicHttpSafetyGuard`):** Converts live web pages into readable Markdown while blocking SSRF, loopback, and private LAN IP ranges.

### 6. Modern Dark Graphite Jetpack Compose UI (`ui/`)
- **Design Tokens:** Dark Graphite theme (`#0E1015` / `#151922` / `#1C222D`) with accent palettes (`Sky #38BDF8`, `Teal #2DD4BF`, `Amber #F59E0B`, `Indigo #818CF8`).
- **Comprehensive Navigation:** 4 primary tabs (`Home`, `Chat`, `Models`, `More`) and dedicated hubs for `Device Control`, `Bridge Setup`, `Security & Permissions`, and `Activity History`.
- **Iridescent Shader Orb (`JarvisOrb`):** AGSL `RuntimeShader` on API 33+ with Canvas fallback, 30fps animation cap, and battery-saver pauses.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│            Jetpack Compose Material 3 UI (Dark Graphite / Light)           │
│   Home │ Chat (History Drawer) │ Models │ More (Device, Bridge, Security)  │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │ StateFlow (MVVM + Navigation Compose)
┌────────────────────────────────────▼───────────────────────────────────────┐
│                 Phase2AgentCore & Intelligent ModelRouter                  │
│  PromptCompressor ─► IntentRouter ─► ToolRegistry ─► Procedural Skills/Mem │
│       │                  ├─► PhoneUseTools (Tap, Type, Scroll, Gesture)    │
│       │                  ├─► ScreenPerceptionEngine (MediaProjection + OCR)│
│       │                  ├─► McpManager (JSON-RPC Protocol Client)         │
│       │                  └─► DeviceCommandRouter (Shizuku -> ADB -> Native)│
│       └─► RuntimeEngineCoordinator (LiteRT-LM) / OkHttpCloudAiClient       │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────────┐
│   Room v7 WAL DB │ DataStore + Keystore AES-256-GCM │ 16MB OkHttp Cache    │
└────────────────────────────────────────────────────────────────────────────┘
```

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for full module specifications, security policies, and schema definitions.

---

## Tech Stack

| Layer | Technologies |
| --- | --- |
| **Language & Concurrency** | Kotlin `2.2.10`, Coroutines & Flow `1.10.2` |
| **UI Framework** | Jetpack Compose (BOM `2024.09.00`), Material 3, Navigation Compose `2.8.9` |
| **Autonomous Phone Use** | Accessibility Services, Android `MediaProjection`, On-Device OCR, Window Overlay |
| **Protocols & Bridges** | Model Context Protocol (MCP JSON-RPC), Shizuku `13.1.5` (AIDL), Kadb `1.3.0` |
| **On-Device AI Engine** | Google `litertlm-android:0.10.2` (`.litertlm`, GPU OpenCL / CPU XNNPACK) |
| **Voice & Wake-Word** | Android `SpeechRecognizer` & `TextToSpeech`, Picovoice `porcupine-android:4.0.2` |
| **Data Persistence** | Room `2.7.0` (SQLite WAL v7), DataStore Preferences `1.1.7` |
| **Security & Privacy** | Android Keystore `AES-256-GCM`, App Blocklist Policy, Screen Risk Classifier |
| **Build & CI/CD** | AGP `9.1.1`, Gradle `9.3.1`, JDK `21`, GitHub Actions Ubuntu Runner |

---

## Getting Started & Building

### Prerequisites
- **JDK:** 21 (Temurin recommended)
- **Android SDK:** Compile SDK `36` (`minSdk 24`, `targetSdk 36`)
- **Gradle:** `9.3.1`

### Clone Repository
```bash
gh repo clone zakmijo2-dotcom/Jarvis-
# or
git clone https://github.com/zakmijo2-dotcom/Jarvis-.git
cd Jarvis-
```

### Fast Local Signed APK Build
```bash
# Linux / macOS / WSL (auto-generates signing keys if missing)
bash build-apk.sh

# Windows
build-apk.bat

# Standard Debug build via Gradle
gradle :app:assembleDebug --parallel --build-cache -x lint -x test
```

### Full Production R8-Optimized Build
```bash
# Full R8 minification and resource shrinking
bash build-apk.sh --optimize
```

---

## GitHub Actions CI/CD Pipeline

The automated workflow [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml):
1. Sets up JDK 21 and Gradle 9.3.1 with dependency caching on Ubuntu runners.
2. Automatically provisions signing keystores (`debug.keystore` and release keystores).
3. Compiles `Jarvis-release.apk` and `Jarvis-debug.apk` directly from source.
4. Generates SHA-256 verification checksums (`SHA256SUMS.txt`).
5. Uploads build artifacts to GitHub Actions and publishes official assets to [GitHub Releases](https://github.com/zakmijo2-dotcom/Jarvis-/releases).

---

## Documentation Index
- [`LICENSE`](LICENSE) — GNU General Public License v3.0 (`GPL-3.0`)
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — System architecture, MCP protocols, tool catalog & database schema
- [`BUILD_AND_INSTALL.md`](BUILD_AND_INSTALL.md) — Build scripts, keystore signing, and device setup guide
- [`CHANGELOG.md`](CHANGELOG.md) — Release notes and changelog history
- [`RELEASE_NOTES.md`](RELEASE_NOTES.md) — Feature highlights and device requirements
- [`TEST_PLAN.md`](TEST_PLAN.md) — Automated test suite and verification matrix
- [`OPEN_SOURCE_CREDITS.md`](OPEN_SOURCE_CREDITS.md) — Third-party libraries, licenses, and attributions

---

## License

Copyright (C) 2026 J.A.R.V.I.S Contributors.  
This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License as published by the Free Software Foundation, version 3 of the License (`GPL-3.0`)**. See [`LICENSE`](LICENSE) for full terms.
