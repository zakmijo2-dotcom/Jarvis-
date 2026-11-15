# J.A.R.V.I.S — Autonomous Hybrid AI Assistant for Android (v1.0.0)

<p align="center">
  <strong>Local-First • Hybrid Cloud &amp; Web • Voice &amp; Wake-Word • Closed-Loop Android Automation</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-v1.0.0-23252B?style=for-the-badge" alt="Version v1.0.0" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-8FB8DE?style=for-the-badge" alt="GPL-3.0 License" />
  <img src="https://img.shields.io/badge/Platform-Android%20(SDK%2024--36)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK 24-36" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.2.10" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/Local%20AI-LiteRT--LM%200.10.2-A9CDB0?style=for-the-badge" alt="LiteRT-LM 0.10.2" />
  <img src="https://img.shields.io/badge/Tests-44%20Passing-2EA043?style=for-the-badge" alt="44 Tests Passing" />
</p>

---

## Overview

**J.A.R.V.I.S** (`v1.0.0`) is an open-source (`GPL-3.0`) native Android AI assistant built in **Kotlin** and **Jetpack Compose (Material 3)**. It unifies on-device **LiteRT-LM** inference, optional user-consented Cloud AI providers, live web research, multilingual voice interaction, and confirmation-gated Android system automation inside a privacy-first architecture.

### Core Design Principles
- **Local-First & Offline Capable:** On-device `.litertlm` inference (GPU OpenCL / CPU XNNPACK), Room v7 SQLite persistence (`WRITE_AHEAD_LOGGING`), and local Android tools operate without an internet connection.
- **Hybrid Orchestration:** Routes dynamically between local tools, local models, web research, and Cloud SSE APIs based on the active routing mode (`LOCAL_ONLY`, `AUTO_HYBRID`, `CLOUD_ONLY`), user consent, network reachability, and RAM/thermal headroom.
- **Closed-Loop Tool Execution:** Runs an **Observe → Think → Act → Verify → Recover** cycle (bounded to 3 tool iterations with duplicate-call protection) rather than unverified single-turn tool dispatches.
- **Strict Security & Privacy Boundaries:** High-risk actions require explicit user confirmation, password fields are redacted from screen observations, untrusted external text is sanitized against prompt injection, and API keys/tokens are encrypted at rest via **Android Keystore AES-256-GCM**.

---

## Key Capabilities

### 1. Autonomous Agent & Multilingual Routing (`core/agent/`, `core/cloud/`, `core/web/`)
- **Closed-Loop Agent Core (`Phase2AgentCore`):** Executes direct `/tool` commands and natural-language multi-step plans across local memory, telemetry/screen observation, web research, procedural skills, and AI synthesis.
- **Multilingual Intent Router (`IntentRouter`):** Classifies intents, matches saved skills, and decomposes tasks across **English**, **Arabic (`ar-SA`)**, **Algerian Darija (`ar-DZ`)**, and **French (`fr-FR`)**.
- **Cloud AI Streaming & Function Calling (`OkHttpCloudAiClient`):** Supports OpenAI-compatible, OpenRouter, Gemini REST, Anthropic, and custom SSE endpoints with tool schema injection and token/latency tracking.
- **Live Web Research (`OkHttpWebResearchService`):** Supports Wikipedia OpenSearch, DuckDuckGo, Brave Search, Tavily, and custom JSON endpoints with automatic offline fallback when network requests fail.

### 2. On-Device AI & Model Hub (`core/ai/`, `core/model/`, `core/hf/`)
- **LiteRT-LM Runtime (`LiteRtLmAiEngine`):** Loads `.litertlm` weights (such as Gemma 3 1B Instruct INT4) off the UI thread with `AUTO_GPU_FALLBACK`, `GPU_ONLY`, or `CPU_ONLY` execution preferences.
- **Memory & Thermal Guard (`ThermalAndMemoryPolicy`):** Inspects `ActivityManager.MemoryInfo` and `PowerManager` thermal status before model allocation and throttles token budgets under thermal pressure.
- **Hugging Face Model Hub (`HuggingFaceApiClient`, `ModelDownloadService`):** Searches repositories, downloads `.litertlm` files in a foreground service with HTTP `Range` resume, and validates `SHA-256` checksums before activation.

### 3. Voice Console & Wake-Word Standby (`core/voice/`, `core/wakeword/`)
- **Hybrid Voice Console (`VoiceViewModel`):** Routes spoken commands through `Phase2AgentCore` with inline plan status, Approve/Cancel confirmation cards, runtime permission prompts, hold-to-talk + continuous modes, and TTS output sanitized via `TtsSanitizer`.
- **Multilingual Speech Pipeline (`AndroidVoiceEngine`):** Integrates Android `SpeechRecognizer` and `TextToSpeech` for `auto`, `ar-SA`, `ar-DZ`, `fr-FR`, and `en-US`.
- **Wake-Word Engine (`ModularWakeWordEngine`, `JarvisVoiceService`):** Integrates Picovoice `porcupine-android:4.0.2` (`"Jarvis"`) with speech-band RMS/ZCR pre-filtering and 2.5s cooldown debouncing; posts a high-priority wake notification when triggered from background standby.

### 4. Android Automation, Procedural Skills & Memory (`core/tools/`, `core/skills/`, `core/memory/`)
- **22 Registered Android Tools (`ToolRegistry`):** App launching, screen navigation, system settings, battery/device telemetry, media volume & playback, contacts search, confirmation-gated phone calls & SMS, sanitized notifications, clock timers/reminders, memory read/write, skill save/run, and 5-layer on-demand Accessibility actions.
- **5-Layer On-Demand Accessibility Stack (`AccessibilityController`):** `JarvisAccessibilityService.onAccessibilityEvent` is a strict **no-op**; screen inspection, tapping, scrolling, and text entry run only on demand after user authorization, redact password nodes, and verify post-action UI state changes.
- **Procedural Skills (`RoomSkillEngine`):** Stores parameterized multi-step workflows in Room (`agent_skills`) and blocks high-risk or confirmation-gated actions from automatic skill distillation.
- **Lifecycle Memory (`RoomLocalMemory`):** Stores categorized preferences, facts, summaries, and project notes (`memory_notes`) with duplicate normalization, importance scoring, and expiration pruning.

### 5. Soft Multi-Color Editorial UI & Iridescent Glass Orb (`ui/`)
- **Palette & Section Hues:** Warm dark (`#15161A` / `#1E2026` / `#272A32`) and light (`#F6F3EE` / `#FBF9F5`) surfaces paired with five low-saturation section accents (`Sky #8FB8DE` Chat, `Peach #F0B49A` Voice, `Sage #A9CDB0` Models & Success, `Sand #E3CF9E` Device & Warning, `Lavender #B8AEE0` Settings, `Muted Coral #E08A7B` Error) and dark `#16171A` ink on hues (`WCAG AA/AAA`).
- **Home Bento & 4-Tab Navigation (`HomeScreen`, `JarvisAppShell`):** Launches on **Home** (`"Hi, <name>"` + `"How can I help?"`), featuring Bento tiles for **Voice**, **Chat**, **Models**, and **Device**, plus recent **History** (`"See all"` opens Chat with the History drawer expanded). Bottom navigation bar / rail provides **Home**, **Chat**, **Models**, and **More** (`Voice`, `Device`, `Settings`).
- **Iridescent 5-Hue Glass Sphere (`JarvisOrb`):** Renders an iridescent sphere using an **AGSL `RuntimeShader`** on Android 13+ (API 33+) with a radial/sweep **Canvas fallback** on API 24–32. Capped at 30fps, throttled to 10fps while a local model is generating, paused when not `RESUMED`, and static under Battery Saver, Thermal Throttling, or `ANIMATOR_DURATION_SCALE = 0`.
- **Typography & Localization:** Bundled **Inter** for UI and **JetBrains Mono** for code/telemetry; localized resources in `values/` (English), `values-ar/` (Arabic, RTL-safe), and `values-fr/` (French).

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                 Jetpack Compose Material 3 UI (v1.0.0)                   │
│   Home (Bento + History) │ Chat │ Models │ More (Voice, Device, Settings)│
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ StateFlow (MVVM)
┌────────────────────────────────────▼─────────────────────────────────────┐
│                Phase2AgentCore (Closed-Loop Orchestrator)                │
│  IntentRouter ─► ToolRegistry (22 Tools) ─► RoomSkillEngine              │
│       │             ├─► AccessibilityController (5-Layer, On-Demand)     │
│       │             ├─► PromptSecuritySanitizer                          │
│       ├─► RoomLocalMemory (Ranked + Lifecycle)                           │
│       ├─► OkHttpWebResearchService                                       │
│       └─► RuntimeEngineCoordinator (LiteRT-LM) / OkHttpCloudAiClient     │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
┌────────────────────────────────────▼─────────────────────────────────────┐
│          Room v7 WAL Database & Encrypted DataStore (AES-256-GCM)        │
└──────────────────────────────────────────────────────────────────────────┘
```

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for detailed module and database schema documentation.

---

## Tech Stack

| Component | Implementation |
| --- | --- |
| Language & Concurrency | Kotlin `2.2.10`, Coroutines & Flow `1.10.2` |
| UI Framework | Jetpack Compose (BOM `2024.09.00`), Material 3, Navigation Compose `2.8.9` |
| Local AI Inference | Google `litertlm-android:0.10.2` (`.litertlm`, GPU OpenCL / CPU XNNPACK) |
| Wake-Word Detection | Picovoice `porcupine-android:4.0.2` |
| Persistence | Room `2.7.0` (SQLite WAL, DB v7 with migrations `1 → 7`), DataStore Preferences `1.1.7` |
| Networking & Serialization | OkHttp `4.10.0`, `kotlinx-serialization-json:1.8.1` |
| Security | Android Keystore `AES/GCM/NoPadding` (`SecureCredentialStore`) |
| Build & Testing | AGP `9.1.1`, Gradle `9.3.1`, JDK 21, `compileSdk 36`, `minSdk 24`, Robolectric `4.16.1` |

---

## Getting Started & Building

### Prerequisites
- **JDK:** 21 (Temurin recommended)
- **Android SDK:** Compile SDK 36 (`minSdk 24`, `targetSdk 36`)
- **Gradle:** `9.3.1` (system `gradle` CLI or Android Studio; this repository does not bundle a `./gradlew` wrapper script)

### Clone
```bash
git clone https://github.com/zakmijo2-dotcom/Jarvis-.git
cd Jarvis-
```

### One-Click Fast Signed APK Build (Recommended)
```bash
# Linux / macOS / WSL — auto-generates keystore if missing & outputs ./JARVIS-v1.0.0.apk
bash build-apk.sh

# Windows — outputs .\JARVIS-v1.0.0.apk
build-apk.bat

# Fastest incremental signed Debug APK -> ./JARVIS-v1.0.0.apk
gradle :app:fastApk --parallel --build-cache -x lint -x test
```

### Run Tests, Lint & Standard Gradle Builds
```bash
# Run full JVM + Robolectric test suite (44 tests)
gradle :app:testDebugUnitTest --no-daemon

# Run Android Lint
gradle :app:lintDebug --no-daemon

# Build Debug APK
gradle :app:assembleDebug --no-daemon
```

### Full R8-Minified Production Release APK & App Bundle
```bash
# Using the one-click script with full R8 optimization:
bash build-apk.sh --optimize

# Or manually with Gradle:
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"
gradle :app:assembleRelease :app:bundleRelease --no-daemon
```

---

## GitHub Actions CI/CD Release

The unified single-job workflow at [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) installs **JDK 21** and **Gradle 9.3.1** (with Gradle Build Cache), builds a signed `JARVIS-v1.0.0.apk` + `SHA256SUMS.txt` in fast mode by default, uploads it to **Actions Artifacts**, and publishes directly to **GitHub Releases** on `v*` tags or manual `workflow_dispatch`.

Optional **GitHub Repository Secrets** for permanent custom signing:
- `ANDROID_KEYSTORE_BASE64` — output of `base64 -w0 my-upload-key.jks` (keystore alias must be `upload`)
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

If signing secrets are not set, the workflow automatically generates a valid signing keystore so APK builds and GitHub Releases succeed out of the box.

---

## Verification Scope & Honest Limitations
- **Verified in Headless JVM / Robolectric CI:** **44 / 44** automated tests (`1` in `ExampleUnitTest`, `37` in `JarvisFoundationTest`, `6` in `ExampleRobolectricTest`), Room migrations `1 → 7`, Android Lint (`:app:lintDebug`), and debug/release APK & AAB packaging.
- **Requires Physical Android Device & User-Supplied Assets:**
  - **Local LLM Inference:** Requires downloading or importing a verified `.litertlm` model file (e.g., Gemma 3 1B INT4) on an `arm64-v8a` device; no multi-hundred-megabyte model weights are bundled inside the APK.
  - **Alternative Runtime Formats (`GGUF`, `TFLite`, `MediaPipe`):** Registered via `PreparedFormatRuntimeStub` for format inspection and compatibility reporting; only `.litertlm` is actively executed on-device in `v1.0.0`.
  - **Wake-Word Spotting:** Picovoice Porcupine (`4.0.2`) requires a valid user-supplied Picovoice `AccessKey` and physical microphone audio; without a key, the engine honestly reports `UNAVAILABLE_PENDING_MODEL_OR_KEY` (`verified = false`).
  - **Hardware & OS Services:** Live `SpeechRecognizer` audio capture, `TextToSpeech` playback, AGSL hardware GPU shader rendering (API 33+), and live Android Accessibility / Notification Listener interactions require a physical device or full Android emulator.

---

## Documentation Index
- [`LICENSE`](LICENSE) — GNU General Public License v3.0 (`GPL-3.0`)
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — System architecture, UI design tokens, 22-tool catalog, and Room v7 schema
- [`BUILD_AND_INSTALL.md`](BUILD_AND_INSTALL.md) — Step-by-step build, signing, and device installation guide
- [`CHANGELOG.md`](CHANGELOG.md) — `v1.0.0` release changelog
- [`RELEASE_NOTES.md`](RELEASE_NOTES.md) — `v1.0.0` release summary & hardware notes
- [`TEST_PLAN.md`](TEST_PLAN.md) — Automated (44 tests) and on-device verification matrix
- [`OPEN_SOURCE_CREDITS.md`](OPEN_SOURCE_CREDITS.md) — Open-source libraries, attributions, and `GPL-3.0` compliance
- [`PROGRESS.md`](PROGRESS.md) — Engineering and UI restyle verification log

---

## License

Copyright (C) 2026 J.A.R.V.I.S Contributors.  
This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License as published by the Free Software Foundation, version 3 of the License (`GPL-3.0`)**. See [`LICENSE`](LICENSE) for full terms.
