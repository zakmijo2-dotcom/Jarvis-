# J.A.R.V.I.S — Changelog

All notable changes for **J.A.R.V.I.S Android** are documented in this file.  
License: **GNU General Public License v3.0 (`GPL-3.0`)**

---

## [1.0.1] — Shizuku, Wireless ADB Bridge & Automated Releases (`versionCode = 2`)

### Added
- **Shizuku Integration (`core/device/ShizukuController`, `JarvisShizukuUserService`):**
  - Seamless non-root system command execution and privileged operations via Shizuku API (`13.1.5`).
  - Added user service IPC and permission verification checks.
- **Wireless ADB Bridge & Setup Wizard (`core/device/WirelessAdbController`, `ui/screens/bridge/DeviceBridgeSetupScreen`):**
  - In-app pairing wizard with step-by-step wireless pairing code, port input, and automatic socket connection.
  - Device bridge status tracking and connection telemetry.
- **Extended System Privilege Tools (`core/tools/SystemPrivilegeTools`):**
  - Advanced package management, application freeze/unfreeze, and rootless system parameter controls.
- **Automated CI/CD APK Compilation & Release Pipeline:**
  - Modernized GitHub Actions workflow (`android-release.yml`) for automated signing and building of both `Jarvis-release.apk` and `Jarvis-debug.apk`.
  - Automated SHA-256 integrity checksum generation and asset publishing.

---

## [1.0.0] — Initial Public Release (`versionCode = 1`)

### Added
- **Soft Editorial Multi-Color Material 3 UI (`ui/`):**
  - Warm Dark (`#15161A` / `#1E2026` / `#272A32`) and Light (`#F6F3EE` / `#FBF9F5`) palettes with five low-saturation section accents: **Sky `#8FB8DE`** (Chat & primary actions), **Peach `#F0B49A`** (Voice), **Sage `#A9CDB0`** (Models & Success), **Sand `#E3CF9E`** (Device & Warning), **Lavender `#B8AEE0`** (Settings), and **Muted Coral `#E08A7B`** (Error), with dark `#16171A` text on hues (`WCAG AA/AAA`).
  - **Home Screen (`HomeScreen`):** Personalized greeting (`"Hi, <name>"` passively sourced from user-approved `LocalMemory` facts or localized fallback) + `"How can I help?"`, Bento tiles for **Voice** (with embedded orb), **Chat**, **Models**, and **Device**, plus recent **History** (`"See all"` opens Chat with the History drawer expanded).
  - **4-Item Navigation (`JarvisAppShell`, `MoreScreen`):** Bottom navigation bar and adaptive tablet rail with **Home**, **Chat**, **Models**, and **More** (`Voice`, `Device`, `Settings`).
  - **Iridescent 5-Hue Glass Sphere (`JarvisOrb`):** AGSL `RuntimeShader` on API 33+ with radial/sweep Canvas fallback on API 24–32; supports `IDLE`, `LISTENING`, `PROCESSING`, `SPEAKING`, and `WARNING` modes; capped at 30fps (10fps while a local model is generating), paused when not `RESUMED`, and static under Battery Saver, Thermal Throttling, or `ANIMATOR_DURATION_SCALE = 0`.
  - **Bundled Typography & Multilingual Resources:** Local **Inter** (`res/font/inter_*.ttf`) and **JetBrains Mono** (`res/font/jetbrains_mono_*.ttf`) fonts; localized strings in English (`values/`), Arabic (`values-ar/`, RTL-safe), and French (`values-fr/`).
- **Closed-Loop Autonomous Agent Core (`Phase2AgentCore`, `IntentRouter`):**
  - Observe → Think → Act → Verify → Recover execution loop (bounded to 3 tool iterations with duplicate-call protection and 1x safe read recovery).
  - Multilingual intent classification and task decomposition across English, Arabic (`ar-SA`), Algerian Darija (`ar-DZ`), and French (`fr-FR`).
- **On-Device AI & Hugging Face Model Hub (`core/ai/`, `core/model/`, `core/hf/`):**
  - `LiteRtLmAiEngine` & `RuntimeEngineCoordinator` (`com.google.ai.edge.litertlm:litertlm-android:0.10.2`) with GPU OpenCL → CPU XNNPACK fallback, memory headroom checks, thermal token throttling, and prepared format adapters (`GGUF`, `TFLite`, `MediaPipe`).
  - Resumable foreground-service model downloads (`ModelDownloadService`) with HTTP `Range` support and `SHA-256` checksum verification.
- **Hybrid Cloud AI & Live Web Research (`core/cloud/`, `core/web/`):**
  - `OkHttpCloudAiClient` supporting OpenAI-compatible, OpenRouter, Gemini REST, Anthropic, and custom SSE endpoints behind an explicit user consent gate (`cloudUploadConsentGranted`).
  - `OkHttpWebResearchService` supporting Wikipedia OpenSearch, DuckDuckGo, Brave Search, Tavily, and custom JSON endpoints with automatic offline fallback.
- **Voice Console & Wake-Word Engine (`core/voice/`, `core/wakeword/`):**
  - `AndroidVoiceEngine` (`SpeechRecognizer` + `TextToSpeech` + `TtsSanitizer`), hold-to-talk and continuous listening modes, and inline tool confirmation/permission cards.
  - `ModularWakeWordEngine` integrating Picovoice `porcupine-android:4.0.2` (`"Jarvis"`) with speech-band RMS/ZCR gating, cooldown debouncing, and background wake notifications.
- **22 Registered Android Tools, Skills & 5-Layer Accessibility (`core/tools/`, `core/skills/`, `core/memory/`):**
  - 22 risk-classified tools across app launching, settings, battery/device telemetry, media controls, contacts, confirmation-gated calls/SMS, notifications, clock timers, local memory, procedural skills, and on-demand Accessibility automation.
  - 5-layer `AccessibilityController` stack (`UiObservationEngine` → `ElementIdentificationEngine` → `ActionExecutor` → `StateVerificationEngine`) with strict no-op passive `onAccessibilityEvent` and password redaction.
  - `RoomSkillEngine` (`agent_skills`) and `RoomLocalMemory` (`memory_notes`) with duplicate suppression, importance weighting, and expiration pruning.
- **Room v7 WAL Database & Security Hardening (`data/local/db/`, `core/security/`):**
  - `JarvisDatabase` v7 (`WRITE_AHEAD_LOGGING`) with deterministic migrations `MIGRATION_1_2` through `MIGRATION_6_7`.
  - `PromptSecuritySanitizer` stripping spoofed tool/role markers and isolating untrusted external data.
  - `SecureCredentialStore` encrypting API keys and tokens via Android Keystore `AES/GCM/NoPadding` (256-bit).
  - R8 minification, resource shrinking, `android:allowBackup="false"`, and GitHub Actions CI/CD workflow (`.github/workflows/android-release.yml`).
