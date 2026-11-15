# J.A.R.V.I.S Android — System Architecture Reference (v1.0.0)

## 1. Architectural Overview
**J.A.R.V.I.S** (`v1.0.0`, `com.aistudio.jarvis.k8v4mz`, licensed under `GPL-3.0`) is a local-first, hybrid autonomous Android AI assistant engineered in Kotlin and Jetpack Compose (`Material 3`), targeting Android 7.0 through Android 16 (`minSdk 24`, `compileSdk 36`, `targetSdk 36`) and profiled for modern `arm64-v8a` hardware (such as the Xiaomi Poco X7 Pro, MediaTek Dimensity 8400-Ultra, 12 GB RAM).

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   UI Layer — Jetpack Compose Material 3                    │
│  HomeScreen (Greeting, Bento Grid, Recent History)                         │
│  Bottom Bar / Rail (4 Tabs): Home │ Chat │ Models │ More                   │
│  More Hub & Direct Routes: VoiceScreen │ DeviceDashboardScreen │ Settings  │
│  JarvisOrb (5-Hue Iridescent Sphere: AGSL API 33+ / Canvas <33 Fallback)   │
└──────────────────────────────────────┬─────────────────────────────────────┘
                                       │ StateFlow / Unidirectional Data Flow
┌──────────────────────────────────────▼─────────────────────────────────────┐
│                   Closed-Loop Autonomous Agent Layer                       │
│  Phase2AgentCore (Observe → Think → Act → Verify → Recover, max 3 loops)   │
│        ├──► IntentRouter (Capability-Aware + Skill-Aware Routing)          │
│        ├──► ToolRegistry (22 Registered Android Tools + Risk Classifier)   │
│        ├──► RoomSkillEngine (Parameterized Procedural Skill Library)       │
│        ├──► RoomLocalMemory (Ranked + Lifecycle Memory, Duplicate Checks)  │
│        ├──► PromptSecuritySanitizer (Injection Defense on Untrusted Data)  │
│        └──► OkHttpWebResearchService (Wiki / DDG / Brave / Tavily / JSON)  │
└───────────────┬──────────────────────┬───────────────────────┬─────────────┘
                │                      │                       │
┌───────────────▼──────────────┐ ┌─────▼──────────────┐ ┌──────▼─────────────┐
│   Multi-Runtime AI Engine    │ │  Voice & WakeWord  │ │  Android Services  │
│ RuntimeEngineCoordinator     │ │ AndroidVoiceEngine │ │ ModelDownloadSvc   │
│  ├─ LiteRtLmRuntimeAdapter   │ │  (SpeechRecognizer │ │ JarvisVoiceService │
│  │   (.litertlm GPU/CPU)     │ │   + TextToSpeech)  │ │ JarvisNotifSvc     │
│  ├─ ModelCapabilityProfile   │ │ ModularWakeWordEng │ │ JarvisA11ySvc +    │
│  └─ PreparedFormatRuntimeStub│ │  (Porcupine 4.0.2) │ │  AccessibilityCtrl │
└───────────────┬──────────────┘ └────────────────────┘ └────────────────────┘
                │
┌───────────────▼────────────────────────────────────────────────────────────┐
│                  Persistence & Security Layer (Room v7 WAL)                │
│  JarvisDatabase (conversations, messages, model_entries, download_queue,   │
│                  model_benchmarks, memory_notes, agent_executions,         │
│                  agent_skills)                                             │
│  SettingsRepository (DataStore + SecureCredentialStore AES-256-GCM)        │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. UI Layer & Design System (`ui/`)
- **Palette Tokens (`ui/theme/Color.kt`, `Theme.kt`):**
  - **Dark Theme (default):** Background `#15161A`, Surface `#1E2026`, SurfaceHigh `#272A32`, Hairline `#33363F`, Text `#ECEAE4`, TextSecondary `#A5A8B0`.
  - **Light Theme:** Background `#F6F3EE`, Surface `#FBF9F5`, SurfaceHigh `#EBE7DF`, Hairline `#DDD8CE`, Text `#23252B`, TextSecondary `#5D606A`.
  - **Section Hues & Status Tokens:** Sky `#8FB8DE` (Chat & primary action), Peach `#F0B49A` (Voice), Sage `#A9CDB0` (Models & Success), Sand `#E3CF9E` (Device & Warning), Lavender `#B8AEE0` (Settings), and Muted Coral `#E08A7B` (Error), paired with dark `#16171A` text (`onHue`) on any muted hue fill for WCAG AA/AAA contrast.
- **Typography & Shapes (`ui/theme/Type.kt`, `Shape.kt`):**
  - Bundled **Inter** (`res/font/inter_*.ttf`) for UI hierarchy (`32.sp` greeting headline, `15–16.sp` body at ~`1.4x` line height) and **JetBrains Mono** (`res/font/jetbrains_mono_*.ttf`) for code blocks and telemetry metrics.
  - `20–24dp` rounded surfaces, `CircleShape` pill buttons, 8dp grid spacing, and 1dp hairline borders.
- **Navigation (`ui/navigation/JarvisRoutes.kt`, `JarvisAppShell.kt`, `MoreScreen.kt`):**
  - Launches on `HomeRoute` (`HomeScreen`) with a 4-item bottom navigation bar / adaptive navigation rail: **Home**, **Chat**, **Models**, and **More** (`Voice`, `Device`, `Settings`).
  - Passively derives the Home greeting name from user-approved `LocalMemory` facts (`observeFacts()`), falling back to a localized friendly greeting.
- **Iridescent 5-Hue Orb (`ui/components/JarvisOrb.kt`):**
  - Preserves `OrbMode` (`IDLE`, `LISTENING`, `PROCESSING`, `SPEAKING`, `WARNING`) and renders a top-left lit glass sphere using an **AGSL `RuntimeShader`** on API 33+ and a **Canvas fallback** on API 24–32.
  - Reads animation progress solely inside the `Canvas` draw scope, quantizes frames to 30fps (or 10fps when `isModelGenerating = true`), pauses when the lifecycle is not `RESUMED`, and renders a static sphere when Battery Saver, Thermal Throttling (API 29+), or `ANIMATOR_DURATION_SCALE = 0` is active.

---

## 3. Multi-Runtime Local AI Architecture (`core/ai/`)
- **`ModelRuntimeEngine` & `ModelCapabilityProfile` (`MultiRuntimeEngine.kt`):** Defines `initialize()`, `loadModel()`, `generateStream()`, `cancel()`, `unload()`, `reportPerformance()`, and hardware-aware `capabilityProfile` (`supportsToolCalling`, `maxContextTokens`, `estimatedLatencyTier`, `offlineCapable`).
- **`RuntimeEngineCoordinator`:** Implements `AiEngine`, exposes `activeCapabilities(settings)`, and enforces single-heavy-model mutual exclusion on device RAM unless `ThermalAndMemoryPolicy.assessMemoryHeadroom` confirms at least 2 GB of free memory headroom after loading.
- **`LiteRtLmRuntimeAdapter` & `LiteRtLmAiEngine` (`0.10.2`):**
  - Loads `.litertlm` model weights off the UI thread (`Dispatchers.IO`) with `Backend.GPU()` → `Backend.CPU()` fallback.
  - Injects the registered 22-tool catalog into the system instruction (`systemInstructionProvider`) so local models can emit structured `[TOOL_CALL]{"tool":"...","args":{...}}[/TOOL_CALL]` directives executed inside the bounded closed-loop orchestrator.
- **`PreparedFormatRuntimeStub`:** Non-fabricated runtime adapters for `GGUF_LLAMA_CPP`, `LITERT_TFLITE`, and `MEDIAPIPE_TASK` that report compatibility and format readiness honestly without faking token generation.

---

## 4. Voice & Local Wake-Word Engine (`core/voice/`, `core/wakeword/`)
- **Speech-to-Text (`AndroidVoiceEngine`):** Uses Android `SpeechRecognizer` with real-time partial transcripts, RMS dB metering, and language support for **Arabic (`ar-SA`)**, **Algerian Arabic (`ar-DZ`)**, **French (`fr-FR`)**, and **English (`en-US`)**.
- **Text-to-Speech (`AndroidVoiceEngine` + `TtsSanitizer`):** Strips Markdown syntax, code blocks, URLs, and benchmark footers before speaking; supports instant interruption and continuous conversation re-arming.
- **Wake-Word Engine (`ModularWakeWordEngine`):** Integrates `ai.picovoice:porcupine-android:4.0.2` (`Porcupine.BuiltInKeyword.JARVIS`) with speech-band RMS/ZCR pre-filtering and a 2.5s cooldown debouncer; strictly keeps `isPhraseDetectionVerified = false` until a valid Picovoice AccessKey is verified with live microphone audio.

---

## 5. Android Tool System, Skills Engine & 5-Layer Accessibility (`core/tools/`, `core/skills/`)
- **Closed-Loop Pipeline:** `Observe (Memory + Device Telemetry + On-Demand UI) → Think (Capability-Aware Routing) → Act (Tool/Skill + Permission/Confirmation Gate) → Verify (StateVerificationEngine + Bounded 3-Turn Tool Loop) → Recover (1x Safe Read Retry / Local Fallback)`.
- **5-Layer Accessibility Automation Stack (`AccessibilityController.kt`):**
  `AccessibilityController` → `UiObservationEngine` → `ElementIdentificationEngine` → `ActionExecutor` → `StateVerificationEngine` (before/after window signature hash verification).
- **22 Registered Tools (`ToolRegistryFactory`):**
  1–9: `launch_installed_app`, `open_app_screen`, `go_home_screen`, `open_android_settings`, `read_battery_status`, `read_device_info`, `adjust_media_volume`, `control_media_playback`, `search_contacts` (`READ_CONTACTS`)
  10–11: `initiate_phone_call` (`HIGH`, confirmation-gated), `compose_message` (`HIGH`, confirmation-gated)
  12: `read_notifications` (`LOW`, sanitized via `PromptSecuritySanitizer`)
  13–17: `accessibility_global_action`, `inspect_screen_elements` (`PromptSecuritySanitizer`), `tap_ui_element`, `scroll_screen`, `input_ui_text` (all `HIGH`, confirmation-gated, password fields redacted)
  18: `create_reminder` (`HIGH`, Android Clock `ACTION_SET_TIMER`)
  19–22: `retrieve_memory`, `save_memory`, `save_skill` (`HIGH`, dangerous auto-skills blocked), `run_skill`
- **Strict Privacy Invariants:** `JarvisAccessibilityService.onAccessibilityEvent` is a strict **no-op**. `RoomSkillEngine.distillWorkflowIfSafe` rejects high-risk or confirmation-gated tools from automatic skill persistence.

---

## 6. Data Persistence & Security (`data/local/db/`, `core/security/`)
- **Room Database v7 (`JarvisDatabase`):** Configured with `JournalMode.WRITE_AHEAD_LOGGING` and deterministic migrations `MIGRATION_1_2` through `MIGRATION_6_7` across 8 tables (`conversations`, `messages`, `model_entries`, `download_queue`, `model_benchmarks`, `memory_notes`, `agent_executions`, `agent_skills`).
- **Prompt Injection Defense (`PromptSecuritySanitizer`):** Strips spoofed `[TOOL_CALL]`, `[TOOL_RESULT]`, and `<|system|>` tags and isolates external notifications, web snippets, and UI snapshots inside `[UNTRUSTED_EXTERNAL_DATA ...]` delimiters.
- **Credential Encryption (`SecureCredentialStore`):** Android Keystore `AES/GCM/NoPadding` (256-bit) encryption for Hugging Face tokens, Cloud AI keys, Web Search keys, and Picovoice AccessKeys.
