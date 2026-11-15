# JARVIS Android — System Architecture & Audit Reference

## 1. Architectural Overview
JARVIS is a local-first, hybrid autonomous Android AI assistant engineered in Kotlin and Jetpack Compose (`Material 3`), optimized for the **Xiaomi Poco X7 Pro** (`MediaTek Dimensity 8400-Ultra`, `Arm Mali-G720 MC7`, 12 GB LPDDR5X RAM, Android 15/16, HyperOS).

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        UI Layer (Jetpack Compose M3)                       │
│  ChatScreen (Live Agent Monitor + Chat) │ VoiceScreen │ ModelManagerScreen │
│  DeviceDashboardScreen (+ Permissions & Privacy) │ Settings (+ Skills)     │
└──────────────────────────────────────┬─────────────────────────────────────┘
                                       │ StateFlow / Unidirectional Data Flow
┌──────────────────────────────────────▼─────────────────────────────────────┐
│                   Closed-Loop Autonomous Agent Layer                       │
│  Phase2AgentCore (Observe → Think → Act → Verify → Recover, max 3 loops)   │
│        ├──► IntentRouter (Capability-Aware + Skill-Aware Routing)          │
│        ├──► ToolRegistry (22 Verified Android Tools + Risk Classifier)     │
│        ├──► RoomSkillEngine (Hermes-Style Procedural Skill Library)        │
│        ├──► RoomLocalMemory (Ranked + Lifecycle Memory, Duplicate Checks)  │
│        ├──► PromptSecuritySanitizer (Injection Defense on Untrusted Data)  │
│        └──► WebResearchService (Wikipedia / DuckDuckGo / Brave / Tavily)   │
└───────────────┬──────────────────────┬───────────────────────┬─────────────┘
                │                      │                       │
┌───────────────▼──────────────┐ ┌─────▼──────────────┐ ┌──────▼─────────────┐
│   Multi-Runtime AI Engine    │ │  Voice & WakeWord  │ │  Android Services  │
│ RuntimeEngineCoordinator     │ │ AndroidVoiceEngine │ │ ModelDownloadSvc   │
│  ├─ LiteRtLmRuntimeAdapter   │ │  (SpeechRecognizer │ │ VoiceSessionSvc    │
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

## 2. Multi-Runtime Local AI Architecture (`core/ai/`)
- **`ModelRuntimeEngine` & `ModelCapabilityProfile` (`MultiRuntimeEngine.kt`):** Exposes `initialize()`, `loadModel()`, `generateStream()`, `cancel()`, `unload()`, `reportPerformance()`, and hardware-aware `capabilityProfile` (`supportsToolCalling`, `maxContextTokens`, `estimatedLatencyTier`, `offlineCapable`).
- **`RuntimeEngineCoordinator`:** Implements `AiEngine`, exposes `activeCapabilities(settings)`, and enforces single-heavy-model mutual exclusion on device RAM unless `ThermalAndMemoryPolicy.assessMemoryHeadroom` confirms at least 2 GB of free memory headroom after loading.
- **`LiteRtLmRuntimeAdapter` & `LiteRtLmAiEngine` (`0.10.2`):**
  - Loads `.litertlm` model weights off the UI thread (`Dispatchers.IO`) with `Backend.GPU()` -> `Backend.CPU()` fallback.
  - Injects the registered 22-tool catalog into the system instruction (`systemInstructionProvider`) so local models can emit structured `[TOOL_CALL]{"tool":"...","args":{...}}[/TOOL_CALL]` directives executed inside the bounded closed-loop orchestrator.
- **`PreparedFormatRuntimeStub`:** Honest non-fabricated runtime adapters for `GGUF_LLAMA_CPP`, `LITERT_TFLITE`, and `MEDIAPIPE_TASK`.

---

## 3. Voice & Local Wake-Word Engine (`core/voice/`, `core/wakeword/`)
- **Speech-to-Text (`AndroidVoiceEngine`):** Uses Android `SpeechRecognizer` with real-time partial results, RMS dB metering, and language support for **Arabic (`ar-SA`)**, **Algerian Arabic (`ar-DZ`)**, **French (`fr-FR`)**, and **English (`en-US`)**.
- **Text-to-Speech (`AndroidVoiceEngine` + `TtsSanitizer`):** Strips Markdown syntax, code blocks, URLs, and benchmark footers before speaking; supports instant interruption and continuous conversation re-arming.
- **Wake-Word Engine (`ModularWakeWordEngine`):** Integrates `ai.picovoice:porcupine-android:4.0.2` (`Porcupine.BuiltInKeyword.JARVIS`) with speech-band RMS/ZCR pre-filtering and 2.5s cooldown debouncer; strictly keeps `isPhraseDetectionVerified = false` until a valid Picovoice AccessKey is verified with live mic audio.

---

## 4. Android Tool System, Skills Engine & 5-Layer Accessibility (`core/tools/`, `core/skills/`)
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
- **Strict Privacy Invariant:** `JarvisAccessibilityService.onAccessibilityEvent` is a strict **no-op**. `RoomSkillEngine.distillWorkflowIfSafe` strictly rejects high-risk or confirmation-gated tools from automatic skill persistence.

---

## 5. Data Persistence & Security (`data/local/db/`, `core/security/`)
- **Room Database v7 (`JarvisDatabase`):** Configured with `JournalMode.WRITE_AHEAD_LOGGING` and deterministic migrations `MIGRATION_1_2` through `MIGRATION_6_7` (preserving `memory_notes` with lifecycle/importance metadata and adding `agent_skills`).
- **Prompt Injection Defense (`PromptSecuritySanitizer`):** Strips spoofed `[TOOL_CALL]`, `[TOOL_RESULT]`, and `<|system|>` tags and isolates external notifications and UI snapshots inside `[UNTRUSTED_EXTERNAL_DATA ...]` delimiters.
- **Credential Encryption (`SecureCredentialStore`):** Android Keystore `AES/GCM/NoPadding` (256-bit) encryption for Hugging Face tokens, Cloud AI keys, Web Search keys, and Picovoice AccessKeys.
