# J.A.R.V.I.S — Autonomous Hybrid AI Assistant for Android

<p align="center">
  <strong>Local-First • Hybrid Cloud &amp; Web • Voice &amp; Wake-Word • Closed-Loop Android Automation</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20(SDK%2024--36)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK 24-36" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.2.10" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/Local%20AI-LiteRT--LM%200.10.2-00A896?style=for-the-badge" alt="LiteRT-LM 0.10.2" />
  <img src="https://img.shields.io/badge/Unit%20Tests-42%20Passing-2EA043?style=for-the-badge" alt="42 Tests Passing" />
</p>

---

## Overview

**J.A.R.V.I.S** is a native Android autonomous AI assistant built in **Kotlin** and **Jetpack Compose (Material 3)**. It combines on-device **LiteRT-LM** inference, user-consented Cloud AI providers, live web research, multilingual voice interaction, and risk-gated Android system automation inside a privacy-first architecture.

### Core Design Principles
- **Local-First & Offline Capable:** On-device `.litertlm` inference (CPU XNNPACK / GPU OpenCL), Room v7 persistence, and on-device system tools work without internet access.
- **Hybrid Orchestration:** Seamlessly routes between on-device tools, local models, web research, and Cloud SSE APIs based on user routing mode (`LOCAL_ONLY`, `AUTO_HYBRID`, `CLOUD_ONLY`), consent, network status, and device headroom.
- **Closed-Loop Tool Execution:** Runs an **Observe → Think → Act → Verify → Recover** loop (bounded to 3 iterations with duplicate-call protection) instead of single-turn blind responses.
- **Explicit Security & Privacy Boundaries:** High-risk actions require confirmation, passwords are redacted from UI observations, untrusted external text is sanitized against prompt injection, and API keys/tokens are encrypted at rest with **Android Keystore AES-256-GCM**.

---

## Key Capabilities

### 1. Autonomous Agent & Multilingual Routing (`core/agent/`, `core/cloud/`, `core/web/`)
- **Closed-Loop Agent Core (`Phase2AgentCore`):** Executes direct `/tool` commands and natural-language multi-step plans across local memory, telemetry/UI observation, web research, reusable skills, and AI synthesis.
- **Multilingual Intent Router (`IntentRouter`):** Supports **English**, **Arabic**, **Algerian Darija (`ar-DZ`)**, and **French** intent classification, skill matching, and task decomposition.
- **Cloud AI Streaming & Native Function Calling (`OkHttpCloudAiClient`):** Supports OpenAI-compatible, OpenRouter, Gemini REST, Anthropic, and custom endpoints over SSE with standard tool schema injection and usage tracking.
- **Live Web Research (`OkHttpWebResearchService`):** Supports Wikipedia OpenSearch, DuckDuckGo, Brave Search, Tavily, and custom JSON endpoints with automatic offline fallback when requests fail.

### 2. On-Device AI & Model Management (`core/ai/`, `core/model/`, `core/hf/`)
- **LiteRT-LM Runtime (`LiteRtLmAiEngine`):** Executes `.litertlm` models (such as Gemma 3 1B INT4) off the UI thread with `AUTO_GPU_FALLBACK`, `GPU_ONLY`, or `CPU_ONLY` backend preferences.
- **Thermal & Memory Policy (`ThermalAndMemoryPolicy`):** Enforces memory headroom checks and thermal token throttling to prevent OOM crashes on mobile devices.
- **Hugging Face Model Hub (`HuggingFaceApiClient`, `ModelDownloadService`):** Discovers model repositories, downloads `.litertlm` artifacts in a foreground service with HTTP `Range` resume, and verifies `SHA-256` checksums before activation.

### 3. Voice Console & Background Wake-Word (`core/voice/`, `core/wakeword/`)
- **Hybrid Voice Console (`VoiceViewModel`):** Spoken commands execute through the full `Phase2AgentCore` hybrid pipeline (tools, skills, web search, and cloud/local AI), with inline plan progress, Approve/Cancel confirmation cards, permission prompts, and TTS playback (`TtsSanitizer` removes Markdown/URLs/metadata before speaking).
- **Multilingual Speech Pipeline (`AndroidVoiceEngine`):** Real Android `SpeechRecognizer` + `TextToSpeech` supporting `auto`, `ar-SA`, `ar-DZ`, `fr-FR`, and `en-US`.
- **Wake-Word Standby (`ModularWakeWordEngine`, `JarvisVoiceService`):** Integrates Picovoice `porcupine-android:4.0.2` (`"Jarvis"`) with speech-band RMS/ZCR gating and cooldown debouncing; optionally maintains foreground-service standby in the background and posts a high-priority wake notification to open and arm listening.

### 4. Android Automation, Procedural Skills & Memory (`core/tools/`, `core/skills/`, `core/memory/`)
- **22 Registered Android Tools (`ToolRegistry`):** App launching, screen navigation, system settings, battery/device telemetry, media volume & playback, contacts search, confirmation-gated calls/messages, sanitized notifications, clock timers/reminders, memory read/write, skill save/run, and 5-layer on-demand Accessibility actions.
- **5-Layer On-Demand Accessibility Stack (`AccessibilityController`):** `JarvisAccessibilityService.onAccessibilityEvent` is a strict **no-op**; screen inspection, tapping, scrolling, and text input run only when explicitly triggered, redact password fields, and verify post-action UI state transitions.
- **Procedural Skills (`RoomSkillEngine`):** Extracts parameters automatically for saved multi-step workflows and blocks high-risk/confirmation-gated actions from automatic skill distillation.
- **Lifecycle Memory (`RoomLocalMemory`):** Stores categorized facts, preferences, summaries, and project context with deduplication, importance weighting, and expiration cleanup.

### 5. Obsidian Slate UI (`ui/`)
- **Unified Assistant Home & Chat (`ChatScreen`):** Animated assistant orb header, conversation drawer, message & code-block copy actions, response regeneration, and expandable Live Agent Execution Monitor.
- **5 Navigation Destinations:** **Chat**, **Voice**, **Models**, **Device** (Telemetry + Permissions & Privacy), and **Settings** (Routing, Cloud/Web keys, Wake-Word, Reusable Skills).

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     Jetpack Compose M3 UI (5 Screens)                    │
│        Chat (Home + Orb) │ Voice │ Models │ Device │ Settings            │
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

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for detailed module and schema documentation.

---

## Tech Stack

| Component | Implementation |
| --- | --- |
| Language & Concurrency | Kotlin `2.2.10`, Coroutines & Flow `1.10.2` |
| UI Framework | Jetpack Compose (BOM `2024.09.00`), Material 3, Navigation Compose `2.8.9` |
| Local AI Inference | Google `litertlm-android:0.10.2` (`.litertlm`, GPU OpenCL / CPU XNNPACK) |
| Wake-Word Detection | Picovoice `porcupine-android:4.0.2` |
| Persistence | Room `2.7.0` (SQLite WAL, DB v7 with migrations 1→7), DataStore Preferences `1.1.7` |
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

### Run Tests, Lint & Debug APK
```bash
# Run full JVM + Robolectric test suite (42 tests)
gradle :app:testDebugUnitTest --no-daemon

# Run Android Lint
gradle :app:lintDebug --no-daemon

# Build Debug APK
gradle :app:assembleDebug --no-daemon
```

### Build Signed Release APK & App Bundle (R8 Minified)
```bash
# 1. Create an upload keystore (first time only; alias must be 'upload')
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# 2. Export signing credentials and build Release APK + AAB
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"

gradle :app:assembleRelease :app:bundleRelease --no-daemon
```

Outputs:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`
- `app/build/outputs/mapping/release/mapping.txt`

---

## GitHub Actions CI/CD Release

The workflow at [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) installs **JDK 21** and **Gradle 9.3.1**, runs unit/Robolectric tests and Android Lint, builds R8-minified Release `APK` + `AAB` artifacts with `SHA256SUMS.txt`, and publishes to **GitHub Releases** on `v*` tags or manual `workflow_dispatch`.

Configure these **GitHub Repository Secrets** for permanent Play Store signing:
- `ANDROID_KEYSTORE_BASE64` — output of `base64 -w0 my-upload-key.jks` (keystore alias must be `upload`)
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

If signing secrets are not yet set, the workflow generates an ephemeral CI keystore so test builds still succeed.

---

## Security & Privacy Notes
- **No Hardcoded Secrets:** API keys (Cloud AI, Hugging Face, Web Search, Picovoice) are entered in-app and stored encrypted via Android Keystore (`AES-256-GCM`), or injected cleanly through `.env` (never commit `.env`, `*.jks`, or keystores).
- **Cloud Consent Gate:** Outbound Cloud AI calls are blocked unless the user explicitly enables Cloud API transmission in Settings.
- **Prompt Injection Defense:** External notifications, web results, and captured screen hierarchies are stripped of control tags and isolated inside `[UNTRUSTED_EXTERNAL_DATA]` boundaries.

---

## Project Status & Verification Scope
- **Verified in JVM / Robolectric CI:** **42 / 42** unit & Robolectric tests (`JarvisFoundationTest`, `ExampleRobolectricTest`, `ExampleUnitTest`), Android Lint (`:app:lintDebug`), and R8-minified release packaging (`:app:assembleRelease`, `:app:bundleRelease`).
- **Device-Dependent Capabilities:** Live microphone `SpeechRecognizer`, physical Picovoice Porcupine wake-word audio triggering, on-device `.litertlm` GPU/CPU inference throughput, and real Android Accessibility Service UI actions require running on a physical Android device (or emulator with Play/mic support) and user-supplied models/keys.

---

## Additional Documentation
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — System architecture, 22-tool catalog, and Room v7 layout
- [`BUILD_AND_INSTALL.md`](BUILD_AND_INSTALL.md) — Step-by-step build & HyperOS installation guide
- [`CHANGELOG.md`](CHANGELOG.md) — Version history and evolution notes
- [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) — Engineering roadmap & milestone tracking
- [`TEST_PLAN.md`](TEST_PLAN.md) — Automated and on-device verification matrix
- [`OPEN_SOURCE_CREDITS.md`](OPEN_SOURCE_CREDITS.md) — Open-source libraries and attributions
- [`RELEASE_NOTES.md`](RELEASE_NOTES.md) — Release summary notes
