# J.A.R.V.I.S Android — Release Notes (v1.0.0)

- **Version:** `1.0.0` (`versionCode = 1`)
- **Application ID:** `com.aistudio.jarvis.k8v4mz`
- **License:** GNU General Public License v3.0 (`GPL-3.0`)
- **Platform Support:** Android 7.0 – Android 16 (`minSdk 24`, `compileSdk 36`, `targetSdk 36`)
- **Reference Hardware Profile:** Xiaomi Poco X7 Pro (MediaTek Dimensity 8400-Ultra, 12 GB RAM, Android 15/16, HyperOS)

---

## Highlights in v1.0.0

### 1. Soft Multi-Color Editorial UI & Iridescent 5-Hue Orb
- **Calm Multi-Color Palette:** Ships both Dark (`#15161A` / `#1E2026` / `#272A32`) and Light (`#F6F3EE` / `#FBF9F5`) Material 3 themes with five low-saturation section hues: **Sky `#8FB8DE`** (Chat), **Peach `#F0B49A`** (Voice), **Sage `#A9CDB0`** (Models & Success), **Sand `#E3CF9E`** (Device & Warning), **Lavender `#B8AEE0`** (Settings), and **Muted Coral `#E08A7B`** (Error), using dark `#16171A` text on hues for WCAG AA/AAA contrast.
- **Home Bento & 4-Tab Navigation:** Launches on **Home** (`"Hi, <name>"` + `"How can I help?"`) with Bento tiles for **Voice**, **Chat**, **Models**, and **Device**, plus recent **History** (`"See all"` opens Chat with the History drawer expanded). Bottom navigation bar and tablet rail provide **Home**, **Chat**, **Models**, and **More** (`Voice`, `Device`, `Settings`).
- **Iridescent Glass Sphere (`JarvisOrb`):** Uses an **AGSL `RuntimeShader`** on API 33+ and a **Canvas fallback** below API 33; capped at 30fps, throttled to 10fps while a local model is generating, paused when not `RESUMED`, and static when Battery Saver, Thermal Throttling, or `ANIMATOR_DURATION_SCALE = 0` is active.
- **Rich Chat & Multilingual Support:** Fenced monospace code blocks (`JetBrains Mono`) with one-tap **Copy Code**, message-level **Copy**, **Regenerate Response**, expandable Live Agent Execution Monitor, and localized resources in English, Arabic (with automatic RTL script alignment), and French.

### 2. Local AI, Model Hub & Hardware Guardrails
- **LiteRT-LM On-Device Inference (`0.10.2`):** Executes verified `.litertlm` models (such as Gemma 3 1B Instruct INT4) with GPU OpenCL default and automatic CPU XNNPACK fallback.
- **Memory & Thermal Guardrails:** Checks `ActivityManager.MemoryInfo` before native weight allocation (`< 384 MB` critical free-memory guard and `>= 2 GB` multi-runtime headroom check) and dynamically caps token output under `PowerManager` thermal throttling.
- **OS Memory Lifecycle:** `ComponentCallbacks2` (`onTrimMemory`) releases inactive conversation contexts when backgrounded and unloads idle native engines on critical memory pressure.
- **7-Dimension Benchmark History (`model_benchmarks`):** Records model init time (`ms`), `TTFT (ms)`, decode throughput (`tok/s`), peak/native heap (`MB`), artifact size, active backend, and thermal/battery telemetry per inference run.

### 3. Closed-Loop Agent, Voice & 22 Android Tools
- **Closed-Loop Agent Core (`Phase2AgentCore`):** Runs a bounded 3-iteration **Observe → Think → Act → Verify → Recover** loop across local tools, procedural skills (`RoomSkillEngine`), lifecycle memory (`RoomLocalMemory`), web research (`OkHttpWebResearchService`), and local/cloud LLMs.
- **Multilingual Voice & Wake-Word (`AndroidVoiceEngine`, `ModularWakeWordEngine`):** Supports Arabic (`ar-SA`), Algerian Darija (`ar-DZ`), French (`fr-FR`), and English (`en-US`) speech recognition and TTS playback, plus Picovoice `porcupine-android:4.0.2` `"Jarvis"` wake-word detection (requires a user-supplied Picovoice AccessKey; reports `verified = false` until configured).
- **22 Registered Tools & 5-Layer On-Demand Accessibility:** Covers app launching, settings, telemetry, media, contacts, confirmation-gated calls/SMS, notifications, reminders, memory, skills, and 5-layer Accessibility automation (`AccessibilityController`). `JarvisAccessibilityService.onAccessibilityEvent` is a strict no-op (zero passive background scraping), and password fields are redacted.

### 4. Persistence & Security Hardening
- **Room v7 WAL Database (`JarvisDatabase`):** Uses `WRITE_AHEAD_LOGGING` and deterministic migrations `MIGRATION_1_2` through `MIGRATION_6_7` across 8 tables.
- **Security Controls:** Android Keystore `AES/GCM/NoPadding` encryption (`SecureCredentialStore`) for all API keys/tokens, `PromptSecuritySanitizer` against prompt injection from untrusted screen/notification/web data, R8 minification & resource shrinking, and `android:allowBackup="false"`.
