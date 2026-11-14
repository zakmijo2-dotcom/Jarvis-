# JARVIS Android — System Architecture & Audit Reference

## 1. Architectural Overview
JARVIS is a local-first, hybrid autonomous Android AI assistant engineered in Kotlin and Jetpack Compose (`Material 3`), optimized for the **Xiaomi Poco X7 Pro** (`MediaTek Dimensity 8400-Ultra`, `Arm Mali-G720 MC7`, 12 GB LPDDR5X RAM, Android 15/16, HyperOS).

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        UI Layer (Jetpack Compose M3)                       │
│  ChatScreen (Unified Assistant Home) │ VoiceScreen │ ModelManagerScreen    │
│  DeviceDashboardScreen               │ SettingsScreen                      │
└──────────────────────────────────────┬─────────────────────────────────────┘
                                       │ StateFlow / Unidirectional Data Flow
┌──────────────────────────────────────▼─────────────────────────────────────┐
│                          Autonomous Agent Layer                            │
│  Phase2AgentCore ──► IntentRouter (LOCAL_ONLY / AUTO_HYBRID / CLOUD_ONLY)  │
│        ├──► ToolRegistry (17 Verified Android Tools + Risk Classifier)     │
│        ├──► RoomLocalMemory (Approved Facts, Preferences, Summaries)       │
│        └──► WebResearchService (DuckDuckGo / Brave / Tavily / Custom JSON) │
└───────────────┬──────────────────────┬───────────────────────┬─────────────┘
                │                      │                       │
┌───────────────▼──────────────┐ ┌─────▼──────────────┐ ┌──────▼─────────────┐
│   Multi-Runtime AI Engine    │ │  Voice & WakeWord  │ │  Android Services  │
│ RuntimeEngineCoordinator     │ │ AndroidVoiceEngine │ │ ModelDownloadSvc   │
│  ├─ LiteRtLmRuntimeAdapter   │ │  (SpeechRecognizer │ │ VoiceSessionSvc    │
│  │   (.litertlm GPU/CPU)     │ │   + TextToSpeech)  │ │ JarvisNotifSvc     │
│  ├─ PreparedFormatRuntimeStub│ │ ModularWakeWordEng │ │ JarvisA11ySvc      │
│  │   (GGUF / TFLite / Task)  │ │  (Porcupine 4.0.2) │ │  (On-Demand Only)  │
└───────────────┬──────────────┘ └────────────────────┘ └────────────────────┘
                │
┌───────────────▼────────────────────────────────────────────────────────────┐
│                  Persistence & Security Layer (Room v6 WAL)                │
│  JarvisDatabase (conversations, messages, model_entries, download_queue,   │
│                  model_benchmarks, memory_notes, agent_executions)         │
│  SettingsRepository (DataStore + SecureCredentialStore AES-256-GCM)        │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Multi-Runtime Local AI Architecture (`core/ai/`)
- **`ModelRuntimeEngine` (`MultiRuntimeEngine.kt`):** Standard runtime contract exposing `initialize()`, `loadModel()`, `generateStream()`, `cancel()`, `unload()`, and `reportPerformance()`.
- **`RuntimeEngineCoordinator`:** Implements `AiEngine` and coordinates registered format engines. Enforces single-heavy-model mutual exclusion on device RAM unless `ThermalAndMemoryPolicy.assessMemoryHeadroom` confirms at least 2 GB of free memory headroom after loading.
- **`LiteRtLmRuntimeAdapter` & `LiteRtLmAiEngine` (`0.10.2`):**
  - Loads `.litertlm` model weights off the UI thread (`Dispatchers.IO`).
  - Defaults to `Backend.GPU()` (OpenCL on Mali-G720) with automatic `Backend.CPU()` (XNNPACK) fallback.
  - Injects the registered 17-tool catalog into the system instruction (`systemInstructionProvider`) so local models can emit structured `[TOOL_CALL]{"tool":"...","args":{...}}[/TOOL_CALL]` directives.
  - Preserves partial generated text and fallback `GenerationBenchmark` metrics on user cancellation (`CancellationException`).
  - Automatically reloads the native engine if the user switches `InferenceBackendPreference` (`AUTO_GPU_FALLBACK`, `CPU_ONLY`, `GPU_PREFERRED`) while a model is already loaded.
- **`PreparedFormatRuntimeStub`:** Honest, non-fabricated runtime adapters for `GGUF_LLAMA_CPP`, `LITERT_TFLITE`, and `MEDIAPIPE_TASK` that report clear compatibility messages rather than simulating token output.

---

## 3. Voice & Local Wake-Word Engine (`core/voice/`, `core/wakeword/`)
- **Speech-to-Text (`AndroidVoiceEngine`):** Uses Android `SpeechRecognizer` with real-time partial results, RMS dB metering, and language detection for **Arabic (`ar-SA`)**, **Algerian Arabic (`ar-DZ`)**, **French (`fr-FR`)**, and **English (`en-US`)**.
- **Text-to-Speech (`AndroidVoiceEngine` + `TtsSanitizer`):** Strips Markdown syntax, code blocks, URLs, and benchmark footers before speaking; supports instant interruption and continuous conversation re-arming.
- **Wake-Word Engine (`ModularWakeWordEngine`):**
  - Integrates `ai.picovoice:porcupine-android:4.0.2` (`Porcupine.BuiltInKeyword.JARVIS`) behind `NativeKeywordSpotter`.
  - Applies a speech-band RMS (`> -40 dBFS`) and Zero-Crossing Rate (`0.015..0.45`) pre-filter and a **2.5-second cooldown debouncer** before emitting `triggerEvents` to launch Speech-to-Text.
  - Strictly reports `isPhraseDetectionVerified = false` (`UNAVAILABLE_PENDING_MODEL_OR_KEY`) until a valid Picovoice AccessKey is configured in **Voice Console / Settings** and real PCM frames are processed.

---

## 4. Android Tool System & On-Demand Accessibility (`core/tools/`)
- **Pipeline:** `AI / User Request → ToolRegistry → ToolPermissionValidator → Risk & Confirmation Gate → Android API → ToolExecutionResult → Room Audit Log`.
- **17 Registered Tools (`ToolRegistryFactory`):**
  1. `launch_installed_app` (`LOW`)
  2. `open_app_screen` (`LOW`)
  3. `go_home_screen` (`LOW`)
  4. `open_android_settings` (`LOW`)
  5. `read_battery_status` (`LOW`)
  6. `read_device_info` (`LOW`)
  7. `adjust_media_volume` (`LOW`)
  8. `control_media_playback` (`LOW`)
  9. `search_contacts` (`LOW`, requires `READ_CONTACTS`)
  10. `initiate_phone_call` (`HIGH`, requires explicit confirmation)
  11. `compose_message` (`HIGH`, requires explicit confirmation)
  12. `read_notifications` (`LOW`, requires opt-in toggle + `JarvisNotificationListenerService`)
  13. `accessibility_global_action` (`HIGH`, requires explicit confirmation + `JarvisAccessibilityService`)
  14. `inspect_screen_elements` (`HIGH`, on-demand active window node inspection; password nodes redacted as `[REDACTED_PASSWORD]`)
  15. `tap_ui_element` (`HIGH`, on-demand node `ACTION_CLICK` or `GestureDescription` tap)
  16. `scroll_screen` (`HIGH`, on-demand `ACTION_SCROLL_FORWARD/BACKWARD` or swipe gesture)
  17. `input_ui_text` (`HIGH`, on-demand `ACTION_SET_TEXT` on editable non-password nodes)
- **Strict Privacy Invariant:** `JarvisAccessibilityService.onAccessibilityEvent` is a strict **no-op**. JARVIS never passively scrapes screen contents in the background.

---

## 5. Data Persistence & Security (`data/local/db/`, `core/security/`)
- **Room Database v6 (`JarvisDatabase`):** Configured with `JournalMode.WRITE_AHEAD_LOGGING` and deterministic migrations `MIGRATION_1_2` through `MIGRATION_5_6`.
- **Credential Encryption (`SecureCredentialStore`):** Encrypts Hugging Face tokens, Cloud AI keys, Web Search keys, and Picovoice AccessKeys using Android Keystore `AES/GCM/NoPadding` (256-bit) before writing to Jetpack DataStore.
