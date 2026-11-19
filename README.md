# J.A.R.V.I.S — Autonomous Hybrid AI & Phone-Use Assistant for Android (v1.0.1)

<p align="center">
  <strong>Autonomous Non-Root Phone-Use • Offline LiteRT-LM &amp; ML Kit OCR • Shizuku &amp; Wireless ADB • MCP &amp; SSRF-Safe Web Research • Dual-Mode Jetpack Compose UI</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-v1.0.1-0E1015?style=for-the-badge" alt="Version v1.0.1" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-38BDF8?style=for-the-badge" alt="GPL-3.0 License" />
  <img src="https://img.shields.io/badge/Platform-Android%20(SDK%2024--36)-2DD4BF?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK 24-36" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.2.10" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-60A5FA?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/Device%20Bridge-Shizuku%2013.1.5%20%2B%20Kadb-818CF8?style=for-the-badge" alt="Shizuku 13.1.5 and Kadb" />
  <img src="https://img.shields.io/badge/Local%20AI-LiteRT--LM%200.10.2%20%2B%20ML%20Kit%20OCR-2DD4BF?style=for-the-badge" alt="LiteRT-LM and ML Kit OCR" />
  <img src="https://img.shields.io/badge/Tests-61%20JVM%20%2B%204%20Instrumented-2EA043?style=for-the-badge" alt="61 JVM and 4 Instrumented Tests" />
</p>

---

## Overview

**J.A.R.V.I.S** (`v1.0.1`, `versionCode = 2`, `com.aistudio.jarvis.k8v4mz`) is an open-source (`GPL-3.0`) native Android AI assistant built in **Kotlin** and **Jetpack Compose (Material 3)**. It unifies on-device **LiteRT-LM** inference, **non-root autonomous screen perception + offline ML Kit OCR**, a **Shizuku-first + pure-Kotlin Wireless ADB** device-control bridge, an **SSRF-guarded multi-provider web research engine**, a **Streamable HTTP/SSE MCP client**, and **clean vs. operator dual UI modes** inside a strict zero-trust security model.

### Core Design Principles
- **Autonomous Non-Root Phone-Use (`core/vision/`, `core/overlay/`):** Combines depth-first Accessibility UI tree compression (`ScreenTreeCompressor`), loop/stagnation detection (`ScreenJournal`), API 30+ `AccessibilityService.takeScreenshot()` + Android 14+ `MediaProjection` fallback capture, offline Google ML Kit OCR (`16.0.1`), coordinate/element gesture verification, non-blocking floating HUD overlays (`ExecutionOverlayManager`), and mandatory banking/authenticator package blocklists (`AppBlocklistPolicy`).
- **Shizuku-First Privileged Architecture (`core/device/`, `aidl/`):** Executes allowlisted system operations through an official **Shizuku (`13.1.5`)** `UserService` (`IJarvisShizukuService.aidl`) with binder lifecycle tracking, falling back to **pure-Kotlin Wireless ADB (`kadb:1.3.0`)**, **Native Android APIs**, and **on-demand Accessibility** only when needed.
- **Honest Local AI Execution (`core/ai/`, `core/model/`):** Bundles verified **LiteRT-LM (`0.10.2`)** `.litertlm` inference (`GPU OpenCL` / `CPU XNNPACK`), while unbundled formats (`GGUF`, `TFLite`, `MediaPipe .task`, `Safetensors`) are explicitly blocked and labeled `Unsupported Format`—never faked or mislabeled as runnable.
- **Hardware- & Thermal-Aware Routing (`ModelRouter`):** Selects models and caps context windows dynamically based on available RAM, KV-cache estimates, battery level, `PowerManager` thermal status, and real measured benchmarks (`TTFT`, `tok/s`, peak memory).
- **Strict Zero-Trust & Privacy Boundaries:** Cloud prompts, multimodal attachments, and screen uploads require explicit consent; password fields are redacted (`[REDACTED_PASSWORD]`); secrets are encrypted via **Android Keystore AES-256-GCM** (`SecureCredentialStore`); web fetches block private/loopback/metadata IPs (`PublicHttpSafetyGuard`); and logs are scrubbed by `SecurityLogRedactor`.

---

## Key Capabilities

### 1. Autonomous Phone-Use, Screen Vision & Closed-Loop Agent (`core/agent/`, `core/vision/`, `core/overlay/`)
- **12-Iteration Closed-Loop Agent (`Phase2AgentCore`, `IntentRouter`):** Executes bounded **Observe → Plan → Act → Verify → Recover** task loops across English, Arabic (`ar-SA`), Algerian Darija (`ar-DZ`), and French (`fr-FR`), supporting mid-run user steering, instant stop cancellation, missing-element recovery, stagnation `[RECOVERY_SIGNAL]` injection, and persistent **Room `agent_checkpoints`** pause/resume.
- **34 Registered Android Tools (`ToolRegistryFactory`):** Covers app & settings control, telemetry, media/contacts/calls/SMS/reminders, privileged Shizuku/ADB diagnostics (`check_device_bridge`, `force_stop_app`, `trim_app_caches`), local memory & safe procedural skills (`RoomSkillEngine`), Accessibility + coordinate gestures (`tap_ui_element`, `tap_coordinates`, `long_press_coordinates`, `swipe_screen`, `scroll_screen`, `input_ui_text`), offline screen perception (`capture_screen_ocr`, `capture_screen_visual`), web actions (`open_url`, `web_search`, `fetch_webpage`), and remote MCP invocation (`mcp_call`).
- **Pre-Action Safety Matrix (`ScreenRiskClassifier`, `AppBlocklistPolicy`):** Automatically blocks autonomous control and visual uploads inside sensitive banking/authenticator/installer apps and pauses irreversible or outbound actions behind explicit confirmation checkpoints.

### 2. Shizuku-First & Pure-Kotlin Wireless ADB Device Control (`core/device/`, `aidl/`)
- **Capability-Based Unified Router (`DeviceCommandRouter`):** Routes every command across `ShizukuController` → `WirelessAdbController` → `AndroidNativeController` → `AccessibilityController` fallback, verifying actual outcomes (`pidof`, `id`, binder exit codes, post-action screen diffs).
- **Shizuku Integration (`dev.rikka.shizuku:api/provider:13.1.5`):** Tracks Shizuku availability, binder restarts/deaths (`DeadObjectException`), permission grants/denials, and `JarvisShizukuUserService` execution without assuming root (`UID 0`).
- **Pure-Kotlin Wireless ADB (`com.flyfishxu:kadb:1.3.0`):** Dedicated **Shizuku & Wireless ADB Setup** screen supporting Android 11+ SPAKE2 + TLS 1.3 pairing, `NsdManager` mDNS port auto-discovery (`_adb-tls-pairing._tcp.` / `_adb-tls-connect._tcp.`), live `id` verification probes, and immediate redaction/wiping of 6-digit pairing codes.

### 3. Local AI, Model Hub & Performance (`core/ai/`, `core/model/`, `core/hf/`)
- **LiteRT-LM Runtime & Coordinator (`LiteRtLmAiEngine`, `RuntimeEngineCoordinator`):** Supports streaming generation, immediate cancellation without waiting for full decode, `ComponentCallbacks2` memory trimming, and single-heavy-model RAM mutual exclusion (requires verified `>= 2 GB` reserve for concurrent weights).
- **Intelligent `ModelRouter` & `PromptCompressor`:** Estimates weight + KV-cache RAM footprints, enforces an 85% total-RAM load guard, offloads to consented Cloud AI under severe thermal/low-battery states (`AUTO_HYBRID`), and compacts older chat turns inside local context budgets.
- **Model Hub (`ModelManagerScreen`):** Search Hugging Face repositories, inspect compatibility & hardware-fit badges, download `.litertlm` files with HTTP `Range` (`206`) pause/resume/cancel and `SHA-256` verification, view storage/quantization/measured benchmarks, and one-tap load/unload.

### 4. MCP Client, SSRF-Safe Web Research & Multimodal Cloud (`core/mcp/`, `core/web/`, `core/cloud/`)
- **Streamable HTTP & SSE MCP Client (`McpClient`, `McpManager`):** Supports JSON-RPC 2.0 `initialize`, `notifications/initialized`, `tools/list`, `tools/call`, and `Mcp-Session-Id` session headers, backed by Room `mcp_servers`, encrypted bearer tokens, and confirmation gates on mutating tools.
- **SSRF-Hardened Web Research (`PublicHttpSafetyGuard`, `WebPageExtractor`, `OkHttpWebResearchService`):** Blocks loopback/RFC-1918/link-local/CGNAT/ULA IPs before socket connection and across redirects; supports **Brave Search**, **SerpAPI**, **Tavily**, **Serper**, **Wikipedia**, **keyless DuckDuckGo fallback**, and readable HTML/JSON-LD page extraction (`fetch_webpage`) persisted to `research_history`.
- **Multimodal Cloud & Voice (`OkHttpCloudAiClient`, `core/voice/`, `core/wakeword/`):** Supports Gemini REST (`inlineData`), OpenAI-compatible & OpenRouter (`image_url`), Anthropic (`image`), and custom SSE endpoints alongside multilingual `SpeechRecognizer`/`TextToSpeech` and Picovoice `porcupine-android:4.0.2` wake-word spotting.

### 5. Dual-Mode Jetpack Compose M3 Interface (`ui/`)
- **Claude-Inspired Clean Default Mode (`uiAdvancedModeEnabled = false`, Default):** Opens directly into a focused **Chat-first** interface with a model switcher pill, history & workspace drawer, inline Photo Picker (`PickVisualMedia` — zero broad storage permissions), voice trigger, and non-intrusive live step HUD + instant **Stop** button.
- **Advanced Operator Mode (`uiAdvancedModeEnabled = true`):** Reveals the full **4-tab workspace** (`Home`, `Chat`, `Models`, `More`) plus dedicated sub-screens for **Voice (`JarvisOrb`)**, **Device Control Dashboard**, **Shizuku & Wireless ADB Setup**, **Permissions & Security**, **Activity & Benchmarks History**, **Resumable Checkpoints**, **MCP Servers**, and **Blocked Apps**.
- **Dark Graphite + Light Alabaster Themes:** Dark Graphite (`#0E1015` / `#151922`) with restrained cyan-blue (`#38BDF8`) and teal (`#2DD4BF`) accents alongside Light Alabaster (`#F4F6F9` / `#FFFFFF`), bundled **Space Grotesk**, **Plus Jakarta Sans**, and **JetBrains Mono** fonts, and localized English / Arabic (RTL) / French copy.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│     Jetpack Compose Material 3 UI (Clean Default Mode / Operator Mode)     │
│  4 Tabs: Home │ Chat (+Drawer, Photo Picker, HUD) │ Models │ More          │
│  Sub-Hubs: Voice │ Device │ Shizuku/ADB │ Security │ Activity │ Settings   │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │ StateFlow (MVVM + Navigation Compose)
┌────────────────────────────────────▼───────────────────────────────────────┐
│               Phase2AgentCore & Intelligent ModelRouter                    │
│  PromptCompressor ─► IntentRouter ─► ToolRegistry (34 Tools) ─► Checkpts   │
│    ├─► ScreenPerceptionEngine (A11y Tree + ML Kit OCR + MediaProjection)   │
│    ├─► ScreenRiskClassifier + AppBlocklistPolicy + ExecutionOverlayManager │
│    ├─► DeviceCommandRouter (Shizuku -> Wireless ADB -> Native -> A11y)     │
│    └─► McpManager (JSON-RPC/SSE) + WebResearchService (SSRF-Guarded)       │
└────────────────────────────────────┬───────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────────┐
│  Room v8 WAL DB (11 Tables) │ DataStore + Keystore AES-256-GCM │ 16MB HTTP │
└────────────────────────────────────────────────────────────────────────────┘
```

See [`ARCHITECTURE.md`](ARCHITECTURE.md) and [`research/open-source/IMPLEMENTATION_REPORT.md`](research/open-source/IMPLEMENTATION_REPORT.md) for complete module contracts, threat mitigations, and database schemas.

---

## Tech Stack

| Component | Implementation |
| --- | --- |
| Language & Concurrency | Kotlin `2.2.10`, Coroutines & Flow `1.10.2` |
| UI & Typography | Jetpack Compose (BOM `2024.09.00`), Material 3, Navigation Compose `2.8.9`, Space Grotesk / Plus Jakarta Sans / JetBrains Mono |
| Device Control & Vision | Shizuku `13.1.5` (`dev.rikka.shizuku:api`, `provider` + AIDL), Kadb `1.3.0`, Google ML Kit Text Recognition `16.0.1`, `MediaProjection` + `AccessibilityService` |
| Local AI Inference | Google `litertlm-android:0.10.2` (`.litertlm`, GPU OpenCL / CPU XNNPACK) |
| Wake-Word Detection | Picovoice `porcupine-android:4.0.2` |
| Persistence & Networking | Room `2.7.0` (SQLite WAL, DB v8 with migrations `1 → 8`), DataStore Preferences `1.1.7`, OkHttp `4.10.0` (16 MB disk cache + redirect/SSRF guards) |
| Security & Serialization | Android Keystore `AES/GCM/NoPadding`, `SecurityLogRedactor`, `PublicHttpSafetyGuard`, `ScreenRiskClassifier`, `kotlinx-serialization-json:1.8.1` |
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
# Linux / macOS / WSL — builds fast signed Release APK -> ./JARVIS-v1.0.1.apk
bash build-apk.sh

# Windows — outputs .\JARVIS-v1.0.1.apk
build-apk.bat

# R8-minified & resource-shrunk Release APK (verified 41.88 MB)
bash build-apk.sh --optimize

# Instant signed Debug APK via :app:fastApk
bash build-apk.sh --debug
```

### Gradle Tests, Instrumentation Compile & Audit Verification
```bash
# Run full JVM + Robolectric test suite (61 tests, 7 suites)
gradle :app:testDebugUnitTest --no-daemon

# Compile Android instrumentation tests (4 tests in androidTest)
gradle :app:compileDebugAndroidTestKotlin --no-daemon

# Verify compliance, 34 tools, Room v8, JUnit XML totals, and APK hashes
python3 research/open-source/verify_research_and_metrics.py
```

---

## Measured Verification & CI/CD

| Artifact / Suite | Result | Details |
| --- | --- | --- |
| **JVM / Robolectric Suite** (`:app:testDebugUnitTest`) | **61 / 61 Passed** | `JarvisFoundationTest` (37), `AiRouterAndSecurityTest` (6), `AutonomousPhoneUseTest` (6), `ExampleRobolectricTest` (6), `DeviceControlArchitectureTest` (3), `UiNavigationAndSecurityTest` (2), `ExampleUnitTest` (1) |
| **Instrumentation Compile** (`:app:compileDebugAndroidTestKotlin`) | **Succeeded** | 4 tests (`JarvisInstrumentationTest` + `ExampleInstrumentedTest`) ready for device execution |
| **R8 Release APK** (`bash build-apk.sh --optimize`) | **43,913,895 bytes (41.88 MB)** | `SHA256: 0f2e3e9028dfaea1e24e94a1cfa80d2c39c757334cfcce10023163fe93223c96` |
| **Fast Release APK** (`bash build-apk.sh`) | **56 MB** | `SHA256: 0af152396384936828fc2df250dc2ad56c6d9480ae65c49346b655bb9536e408` |
| **Debug APK** (`app-debug.apk`) | **69,545,010 bytes (66.32 MB)** | `SHA256: 0670941ea8610a1bbeb25008982f88b020b0aca1cd1b18e64fad685676f7ace1` |

The GitHub Actions workflow at [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) provisions **JDK 21**, **Android SDK**, and **Gradle 9.3.1**, runs the **61-test** JVM/Robolectric suite, builds a signed `JARVIS-v1.0.1.apk` from source (`fast-release`, `r8-release`, or `debug`), generates `SHA256SUMS.txt`, uploads artifacts, and publishes **GitHub Releases**.

---

## Honest Scope & Physical Device Requirements
- **Verified Headlessly:** **61 / 61** JVM/Robolectric tests, `androidTest` Kotlin compilation (`4` instrumentation tests), Room migrations `1 → 8`, R8 release minification, clean-room compliance (`0` OpenPhone/PolyForm or QuickJS references in `app/src/main`), and signed APK packaging.
- **Requires Physical Android Device:** Live `Shizuku` daemon IPC (`moe.shizuku.privileged.api`), Android 11+ Wireless Debugging (`127.0.0.1` SPAKE2/TLS handshake), `.litertlm` GPU OpenCL execution on `arm64-v8a` hardware, live `MediaProjection` consent screen capture, Picovoice Wake-Word audio capture (with user-supplied AccessKey), and real Android `AccessibilityService` OS bindings.
- **Unbundled Local Formats:** `GGUF`, `.tflite`, `.task`, and `.safetensors` files are inspected transparently and blocked from execution (`isExecutionSupported = false`) in this APK build because their native C++/JNI runtimes are not bundled.

---

## Documentation Index
- [`LICENSE`](LICENSE) — GNU General Public License v3.0 (`GPL-3.0`)
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — Autonomous Phone-Use, Shizuku/ADB, ModelRouter, 34-tool catalog & Room v8 schema
- [`BUILD_AND_INSTALL.md`](BUILD_AND_INSTALL.md) — Local build scripts, R8 Release signing, CI pipeline & device setup
- [`CHANGELOG.md`](CHANGELOG.md) — `v1.0.1` and `v1.0.0` changelogs
- [`RELEASE_NOTES.md`](RELEASE_NOTES.md) — `v1.0.1` release highlights and safety boundaries
- [`TEST_PLAN.md`](TEST_PLAN.md) — 61 JVM/Robolectric + 4 Android Instrumentation verification matrix
- [`OPEN_SOURCE_CREDITS.md`](OPEN_SOURCE_CREDITS.md) — Third-party libraries, bundled fonts & clean-room attributions
- [`PROGRESS.md`](PROGRESS.md) — Architecture upgrade & verification log
- [`research/open-source/IMPLEMENTATION_REPORT.md`](research/open-source/IMPLEMENTATION_REPORT.md) — Full open-source research synthesis, compliance audit & verified APK metrics

---

## License

Copyright (C) 2026 J.A.R.V.I.S Contributors.  
This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License as published by the Free Software Foundation, version 3 of the License (`GPL-3.0`)**. See [`LICENSE`](LICENSE) for full terms.
