# J.A.R.V.I.S — Autonomous Hybrid AI & Shizuku-First Assistant for Android (v1.0.0)

<p align="center">
  <strong>Local-First LiteRT-LM • Shizuku &amp; Wireless ADB Control • Hybrid Cloud &amp; Web • Voice &amp; Wake-Word</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-v1.0.0-0E1015?style=for-the-badge" alt="Version v1.0.0" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-38BDF8?style=for-the-badge" alt="GPL-3.0 License" />
  <img src="https://img.shields.io/badge/Platform-Android%20(SDK%2024--36)-2DD4BF?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK 24-36" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.2.10" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-60A5FA?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/Device%20Bridge-Shizuku%2013.1.5%20%2B%20Kadb-818CF8?style=for-the-badge" alt="Shizuku 13.1.5 and Kadb" />
  <img src="https://img.shields.io/badge/Local%20AI-LiteRT--LM%200.10.2-2DD4BF?style=for-the-badge" alt="LiteRT-LM 0.10.2" />
  <img src="https://img.shields.io/badge/Tests-55%20JVM%20%2B%204%20Instrumented-2EA043?style=for-the-badge" alt="55 JVM and 4 Instrumented Tests" />
</p>

---

## Overview

**J.A.R.V.I.S** (`v1.0.0`, `com.aistudio.jarvis.k8v4mz`) is an open-source (`GPL-3.0`) native Android AI assistant built in **Kotlin** and **Jetpack Compose (Material 3)**. It unifies on-device **LiteRT-LM** inference, a **Shizuku-first + pure-Kotlin Wireless ADB** device-control stack, optional user-consented Cloud AI providers, live web research, and multilingual voice interaction inside a security-hardened, non-simulated architecture.

### Core Design Principles
- **Shizuku-First Privileged Architecture:** Executes allowlisted system operations through an official **Shizuku (`13.1.5`)** `UserService` (`IJarvisShizukuService.aidl`) with binder lifecycle tracking, falling back to **pure-Kotlin Wireless ADB (`kadb:1.3.0`)**, **Native Android APIs**, and **on-demand Accessibility** only when needed.
- **Honest Local AI Execution:** Bundles verified **LiteRT-LM (`0.10.2`)** `.litertlm` inference (`GPU OpenCL` / `CPU XNNPACK`), while unbundled formats (`GGUF`, `TFLite`, `MediaPipe .task`, `Safetensors`) are explicitly blocked and labeled unsupported—never faked or mislabeled as runnable.
- **Hardware- & Thermal-Aware Routing (`ModelRouter`):** Selects models and caps context windows dynamically based on available RAM, KV-cache estimates, battery level, `PowerManager` thermal status, and real measured benchmarks (`TTFT`, `tok/s`, peak memory).
- **Strict Zero-Trust & Privacy Boundaries:** High-risk actions require explicit user confirmation, arbitrary shell execution is blocked, secrets are encrypted via **Android Keystore AES-256-GCM**, logs are scrubbed by `SecurityLogRedactor` (masking API keys, Bearer tokens, and 6-digit ADB pairing codes), and external HTTP redirects strip `Authorization` headers on non-Hugging-Face hosts.

---

## Key Capabilities

### 1. Shizuku-First & Wireless ADB Device Control (`core/device/`, `aidl/`)
- **Capability-Based Unified Router (`DeviceCommandRouter`):** Routes every command across `ShizukuController` → `WirelessAdbController` → `AndroidNativeController` → limited `Accessibility` fallback (`AccessibilityController`), verifying actual command outcomes (e.g., `pidof`, `id`, binder/window state checks).
- **Shizuku Integration (`dev.rikka.shizuku:api/provider:13.1.5`):** Detects Shizuku installation, binder availability, permission grants/denials, and unexpected disconnects (`DeadObjectException`), running allowlisted `argv` arrays inside `JarvisShizukuUserService` without assuming root (`UID 0`).
- **Pure-Kotlin Wireless ADB (`com.flyfishxu:kadb:1.3.0`):** Dedicated **Shizuku & Wireless ADB Setup** screen supporting Android 11+ SPAKE2 + TLS 1.3 pairing, `NsdManager` mDNS port auto-discovery (`_adb-tls-pairing._tcp.` / `_adb-tls-connect._tcp.`), live `id` probe verification, and instant wiping/redaction of 6-digit pairing codes.

### 2. Local AI, Model Hub & Performance (`core/ai/`, `core/model/`, `core/hf/`)
- **LiteRT-LM Runtime & Coordinator (`LiteRtLmAiEngine`, `RuntimeEngineCoordinator`):** Supports streaming generation, immediate coroutine cancellation without waiting for full decode, `ComponentCallbacks2` memory trimming, and single-heavy-model RAM mutual exclusion (requires verified `>= 2 GB` reserve for concurrent weights).
- **Intelligent `ModelRouter` & `PromptCompressor`:** Estimates weight + KV-cache RAM footprints, enforces an 85% total-RAM load guard, offloads to consented Cloud AI under severe thermal/low-battery states (`AUTO_HYBRID`), and compacts older chat turns into concise factual digests inside local context budgets.
- **Redesigned Model Hub (`ModelManagerScreen`, `DownloadQueueManager`):** Search Hugging Face repositories, inspect compatibility & hardware-fit badges (`Recommended`, `Suitable`, `Tight RAM`, `Insufficient RAM`, `Unsupported Format`, `Setup Required`), download `.litertlm` files with HTTP `Range` pause/resume/cancel and `SHA-256` verification, view storage/quantization/measured benchmarks, and one-tap load/unload.

### 3. Autonomous Agent, 25 Tools, Skills & Memory (`core/agent/`, `core/tools/`, `core/skills/`, `core/memory/`)
- **Closed-Loop Agent (`Phase2AgentCore`, `IntentRouter`):** Bounded 3-turn **Observe → Think → Act → Verify → Recover** orchestrator across English, Arabic (`ar-SA`), Algerian Darija (`ar-DZ`), and French (`fr-FR`).
- **25 Registered Android Tools (`ToolRegistryFactory`):** Covers app/settings control, battery/device telemetry, media volume/playback, contacts, confirmation-gated calls/SMS/reminders, sanitized notifications, local memory, procedural skills, 5-layer on-demand Accessibility actions, and 3 new privileged bridge tools (`check_device_bridge`, `force_stop_app`, `trim_app_caches`).
- **Persistent Memory & Procedural Skills:** Room v7 WAL database with lifecycle memory (`importanceScore`, access tracking, expiration) and safety-filtered procedural workflows (`RoomSkillEngine`).

### 4. Voice Console, Cloud AI & Web Research (`core/voice/`, `core/wakeword/`, `core/cloud/`, `core/web/`)
- **Multilingual Voice & Wake-Word:** Android `SpeechRecognizer` + `TextToSpeech` (sanitized via `TtsSanitizer`) + Picovoice `porcupine-android:4.0.2` (`"Jarvis"`) with speech-band RMS/ZCR gating and honest `verified = false` reporting until a valid AccessKey is supplied.
- **Modular Cloud & Live Web (`OkHttpCloudAiClient`, `OkHttpWebResearchService`):** Supports OpenAI-compatible, OpenRouter, Gemini REST, Anthropic, and custom SSE endpoints plus Wikipedia, DuckDuckGo, Brave Search, and Tavily—backed by a shared **16 MB OkHttp disk cache** (`http_api_cache`).

### 5. Dark Graphite & Cyan-Blue UI Across 9 Screens (`ui/`)
- **Theme System (`DarkGraphite` default + optional `LightAlabaster`):** Deep graphite surfaces (`#0E1015` / `#151922` / `#1C222D`) with restrained cyan-blue (`#38BDF8`), steel-blue (`#60A5FA`), teal (`#2DD4BF`), amber (`#F59E0B`), indigo (`#818CF8`), and crimson (`#F87171`) accents; toggleable between Dark, Light, and System in Settings.
- **Typography & Orb:** Bundled local **Space Grotesk** (headers), **Plus Jakarta Sans** (body), and **JetBrains Mono** (metrics/code); AGSL `RuntimeShader` iridescent orb (`JarvisOrb`) on API 33+ with Canvas fallback on API 24–32, lifecycle/thermal/battery-saver throttling, and full English / Arabic (RTL) / French strings.
- **9 Redesigned Screens (4-Tab Bar + Sub-Hubs):**
  - **Primary Tabs:** `Home` (live telemetry, model/Shizuku/ADB/voice status, quick actions), `Chat` (streaming, tool cards, confirmations, model switch, voice, history drawer), `Models` (discovery, hardware fit, downloads, benchmarks), `More` (sub-hub + startup latency telemetry).
  - **Dedicated Sub-Screens:** `Voice`, `Device Control Dashboard`, `Shizuku & Wireless ADB Setup`, `Permissions & Security` (with live Keystore AES-256-GCM diagnostic), `Activity & Benchmarks History`, and `Settings`.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│            Jetpack Compose Material 3 UI (Dark Graphite / Light)           │
│   4 Tabs: Home │ Chat │ Models │ More                                      │
│   Sub-Screens: Voice │ Device │ Shizuku/ADB Setup │ Security │ Activity    │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │ StateFlow (MVVM + Navigation Compose)
┌────────────────────────────────────▼───────────────────────────────────────┐
│                 Phase2AgentCore & Intelligent ModelRouter                  │
│  PromptCompressor ─► IntentRouter ─► ToolRegistry (25 Tools) ─► Skills/Mem │
│       │                  └─► DeviceCommandRouter                           │
│       │                         (Shizuku -> Wireless ADB -> Native -> A11y)│
│       └─► RuntimeEngineCoordinator (LiteRT-LM) / OkHttpCloudAiClient (SSE) │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────────┐
│   Room v7 WAL DB │ DataStore + Keystore AES-256-GCM │ 16MB OkHttp Cache    │
└────────────────────────────────────────────────────────────────────────────┘
```

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for module contracts, security invariants, and database details.

---

## Tech Stack

| Component | Implementation |
| --- | --- |
| Language & Concurrency | Kotlin `2.2.10`, Coroutines & Flow `1.10.2` |
| UI & Typography | Jetpack Compose (BOM `2024.09.00`), Material 3, Navigation Compose `2.8.9`, Space Grotesk / Plus Jakarta Sans / JetBrains Mono |
| Device Control Bridges | Shizuku `13.1.5` (`dev.rikka.shizuku:api`, `provider` + AIDL), Kadb `1.3.0` (`com.flyfishxu:kadb`), Android `NsdManager` |
| Local AI Inference | Google `litertlm-android:0.10.2` (`.litertlm`, GPU OpenCL / CPU XNNPACK) |
| Wake-Word Detection | Picovoice `porcupine-android:4.0.2` |
| Persistence & Caching | Room `2.7.0` (SQLite WAL, DB v7 with migrations `1 → 7`), DataStore Preferences `1.1.7`, OkHttp `4.10.0` (16 MB disk cache) |
| Security & Serialization | Android Keystore `AES/GCM/NoPadding`, `SecurityLogRedactor`, `HttpSecuritySupport`, `kotlinx-serialization-json:1.8.1` |
| Build & Testing | AGP `9.1.1`, Gradle `9.3.1`, JDK `21`, `compileSdk 36`, `minSdk 24`, Robolectric `4.16.1`, AndroidX Test |

---

## Getting Started & Building

### Prerequisites
- **JDK:** 21 (Temurin recommended; Java 11 bytecode target)
- **Android SDK:** Compile SDK `36` (`minSdk 24`, `targetSdk 36`)
- **Gradle:** `9.3.1` (system `gradle` CLI or Android Studio; `gradle/wrapper/gradle-wrapper.properties` targets `9.3.1` without a bundled `./gradlew` binary)

### Clone
```bash
git clone https://github.com/zakmijo2-dotcom/Jarvis-.git
cd Jarvis-
```

### One-Click Signed APK Build
```bash
# Linux / macOS / WSL — builds fast signed Release APK -> ./JARVIS-v1.0.0.apk
bash build-apk.sh

# Windows — outputs .\JARVIS-v1.0.0.apk
build-apk.bat

# R8-minified & resource-shrunk Release APK
bash build-apk.sh --optimize

# Instant signed Debug APK via :app:fastApk
bash build-apk.sh --debug
```

### Gradle Tests, Instrumentation Compile & Packaging
```bash
# Run full JVM + Robolectric test suite (55 tests)
gradle :app:testDebugUnitTest --no-daemon

# Compile Android instrumentation tests (4 tests in androidTest)
gradle :app:compileDebugAndroidTestKotlin --no-daemon

# Run Android Lint
gradle :app:lintDebug --no-daemon

# Manual full R8 Release APK & App Bundle (requires keystore env vars)
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"
gradle :app:assembleRelease :app:bundleRelease --no-daemon
```

---

## GitHub Actions CI/CD

The single unified workflow at [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) provisions **JDK 21**, **Android SDK**, and **Gradle 9.3.1**, runs the **55-test** JVM/Robolectric suite (`:app:testDebugUnitTest`), builds a signed `JARVIS-v1.0.0.apk` **directly from source** (supporting `fast-release`, `r8-release`, and `debug` dispatch modes, with automatic R8 optimization on `v*` tags), generates `SHA256SUMS.txt`, uploads workflow artifacts, and publishes to **GitHub Releases**.

Optional **GitHub Repository Secrets** for consistent release signing:
- `ANDROID_KEYSTORE_BASE64` — `base64 -w0 my-upload-key.jks` (`alias = upload`)
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

If signing secrets are omitted, CI generates an ephemeral upload keystore automatically so builds and releases succeed without manual configuration.

---

## Honest Scope & Physical Device Requirements
- **Verified in Headless CI:** **55 / 55** JVM/Robolectric tests, `androidTest` Kotlin compilation (`4` instrumentation tests), Room migrations `1 → 7`, R8 release rules, and signed APK packaging.
- **Requires Physical Android Device:** Live `Shizuku` daemon IPC (`moe.shizuku.privileged.api`), Android 11+ Wireless Debugging (`127.0.0.1` SPAKE2/TLS handshake), `.litertlm` GPU OpenCL execution on `arm64-v8a` hardware, Picovoice Wake-Word audio capture (with user-supplied AccessKey), and live AGSL GPU shaders (API 33+).
- **Unbundled Local Formats:** `GGUF`, `.tflite`, `.task`, and `.safetensors` files are inspected transparently and blocked from execution (`isExecutionSupported = false`) in this APK build because their native C++/JNI runtimes are not bundled.

---

## Documentation Index
- [`LICENSE`](LICENSE) — GNU General Public License v3.0 (`GPL-3.0`)
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — Shizuku/ADB bridge, ModelRouter, 25-tool catalog, UI & Room v7 schema
- [`BUILD_AND_INSTALL.md`](BUILD_AND_INSTALL.md) — Local build scripts, R8 Release signing, CI pipeline & device setup
- [`CHANGELOG.md`](CHANGELOG.md) — `v1.0.0` release & architecture upgrade changelog
- [`RELEASE_NOTES.md`](RELEASE_NOTES.md) — `v1.0.0` feature highlights and hardware guardrails
- [`TEST_PLAN.md`](TEST_PLAN.md) — 55 JVM/Robolectric + 4 Android Instrumentation verification matrix
- [`OPEN_SOURCE_CREDITS.md`](OPEN_SOURCE_CREDITS.md) — Libraries, licenses, bundled fonts, and attributions
- [`PROGRESS.md`](PROGRESS.md) — Upgrade phase execution and verification log

---

## License

Copyright (C) 2026 J.A.R.V.I.S Contributors.  
This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License as published by the Free Software Foundation, version 3 of the License (`GPL-3.0`)**. See [`LICENSE`](LICENSE) for full terms.
