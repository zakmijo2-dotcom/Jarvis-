# J.A.R.V.I.S Android — System Architecture Reference (v1.0.0)

## 1. Architectural Overview
**J.A.R.V.I.S** (`v1.0.0`, `com.aistudio.jarvis.k8v4mz`, `GPL-3.0`) is a local-first, hybrid Android AI assistant built in Kotlin and Jetpack Compose (`Material 3`), targeting Android 7.0 through Android 16 (`minSdk 24`, `compileSdk 36`, `targetSdk 36`).

```
┌────────────────────────────────────────────────────────────────────────────┐
│               UI Layer — Jetpack Compose Material 3 (9 Screens)            │
│  Primary 4 Tabs: Home │ Chat (+History Drawer) │ Models │ More             │
│  Sub-Hubs: Voice │ Device Dashboard │ Shizuku/ADB Setup │ Permissions &    │
│            Security │ Activity & Benchmarks History │ Settings             │
│  Orb: JarvisOrb (AGSL RuntimeShader API 33+ / Canvas fallback API 24-32)   │
└──────────────────────────────────────┬─────────────────────────────────────┘
                                       │ StateFlow / MVVM + Navigation Compose
┌──────────────────────────────────────▼─────────────────────────────────────┐
│              Orchestration, Routing & Security Layer                       │
│  Phase2AgentCore (Bounded Observe -> Think -> Act -> Verify -> Recover)    │
│   ├── ModelRouter (RAM, KV-cache, thermal, battery, benchmark-aware)       │
│   ├── PromptCompressor (token budgeter + older-turn factual digest)        │
│   ├── IntentRouter + RoomSkillEngine + RoomLocalMemory                   │
│   ├── PromptSecuritySanitizer + SecurityLogRedactor + HttpSecuritySupport  │
│   └── ToolRegistry (25 Tools) ──► DeviceCommandRouter                      │
│                                      1. ShizukuController (AIDL UserSvc)   │
│                                      2. WirelessAdbController (Kadb TLS)   │
│                                      3. AndroidNativeController            │
│                                      4. AccessibilityController (Fallback) │
└───────────────┬──────────────────────┬───────────────────────┬─────────────┘
                │                      │                       │
┌───────────────▼──────────────┐ ┌─────▼──────────────┐ ┌──────▼─────────────┐
│   AI Runtime & Model Hub     │ │  Voice & WakeWord  │ │  Android Services  │
│ RuntimeEngineCoordinator     │ │ AndroidVoiceEngine │ │ ModelDownloadSvc   │
│  ├─ LiteRtLmRuntimeAdapter   │ │  (STT + TTS +      │ │ JarvisVoiceService │
│  │   (.litertlm GPU/CPU)     │ │   TtsSanitizer)    │ │ JarvisNotifSvc     │
│  ├─ ModelCompatibilityEngine │ │ ModularWakeWordEng │ │ JarvisA11ySvc      │
│  └─ Non-Executable Adapters  │ │  (Porcupine 4.0.2) │ │                    │
│     (GGUF/TFLite/Task/Safe.) │ │                    │ │                    │
└───────────────┬──────────────┘ └────────────────────┘ └────────────────────┘
                │
┌───────────────▼────────────────────────────────────────────────────────────┐
│         Persistence, Caching & Credential Layer (Room v7 WAL)              │
│  JarvisDatabase (8 tables, deterministic migrations 1 -> 7)                │
│  SettingsRepository (DataStore + SecureCredentialStore AES-256-GCM)        │
│  OkHttpClient (16 MB disk cache `http_api_cache` + redirect auth strip)    │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Phase 1 — Unified Device Control Architecture (`core/device/`, `aidl/`)
- **Priority-Based Routing (`DeviceCommandRouter`):** Evaluates device control capabilities in strict order:
  1. **Shizuku (`ShizukuController` + `RealShizukuGateway` + `JarvisShizukuUserService`):** Uses `dev.rikka.shizuku:api/provider:13.1.5` and `IJarvisShizukuService.aidl`. Tracks binder death, service restarts, runtime permission requests/denials, and never assumes root UID `0` (distinguishes `uid=0` root vs `uid=2000` ADB shell).
  2. **Pure-Kotlin Wireless ADB (`WirelessAdbController` + `KadbWirelessTransport`):** Uses `com.flyfishxu:kadb:1.3.0` (Android 11+ SPAKE2 + TLS 1.3) and `NsdManager` mDNS port discovery (`_adb-tls-pairing._tcp.` and `_adb-tls-connect._tcp.`). Validates active sessions with a live `id` probe before reporting `CONNECTED`, and immediately scrubs 6-digit pairing codes from UI and error strings.
  3. **Native Android (`AndroidNativeController`):** Standard Android intents, battery/memory/network telemetry, audio/media, and contacts APIs.
  4. **Accessibility Fallback (`AccessibilityController`):** Retained strictly as an opt-in fallback for UI inspection, element tapping, scrolling, and global actions when no privileged bridge applies.
- **Allowlisted Operations (`PrivilegedActionId`, `AllowlistedCommandValidator`):**
  Every operation declares `supportedBackends`, `requiredPermissionLabel`, `riskLevel` (`LOW`, `MEDIUM`, `HIGH`), confirmation gate, and verification method (`id`, `getprop`, `pidof`, binder exit code). Shell metacharacters and arbitrary shell strings are strictly blocked; `FORCE_STOP_APP` rejects system/protected packages (`android`, `com.android.systemui`, `moe.shizuku.privileged.api`, JARVIS itself, etc.).

---

## 3. Phase 2 & 3 — AI Model Engineering & Performance (`core/ai/`, `core/model/`)
- **Honest Format Enforcement (`RuntimeFormatAdapter.kt`, `ModelCompatibilityEngine.kt`):**
  - `LiteRtLmFormatAdapter` (`.litertlm`): `isExecutionSupported = true` via `LiteRtLmAiEngine` (`0.10.2`) off the UI thread (`Dispatchers.IO`) with `GPU -> CPU` automatic fallback.
  - `GgufFormatAdapter` (`.gguf`), `LiteRtTfliteFormatAdapter` (`.tflite`), `MediaPipeTaskFormatAdapter` (`.task`), `SafetensorsFormatAdapter` (`.safetensors`): All explicitly report `isExecutionSupported = false`, block downloads/loads, and render `Unsupported Format` without fake compatibility badges or placeholder inference.
- **Intelligent `ModelRouter`:**
  - Ranks local and cloud models using real telemetry (`availRamBytes`, `totalRamBytes`), context-scaled KV-cache memory estimates, tool-calling compatibility, thermal throttling (`PowerManager` codes `>= 3`), battery level (`<= 15%` unplugged or Power Saver), and recorded `model_benchmarks` history.
  - Caps context windows (`<= 2048` tokens) on tight RAM or thermal strain and offloads to Cloud AI in `AUTO_HYBRID` only when `cloudUploadConsentGranted == true` and network is available.
  - `evaluateLoadMemoryBudget()` prevents loading models exceeding 85% total RAM and prevents concurrent multi-runtime loads unless `>= 2 GB` verified free memory reserve exists.
- **Runtime & Network Optimizations:**
  - **Streaming & Cancellation:** Token streaming with immediate job cancellation (`cancelGeneration()`).
  - **History Compression (`PromptCompressor`):** Preserves recent turns verbatim and condenses older messages into compact `[COMPACTED PRIOR CONTEXT ...]` summaries.
  - **Startup & Caching:** `JarvisApplication` measures cold startup latency (`measuredStartupDurationMs()`) and shares a `16 MB` OkHttp disk cache (`http_api_cache`) + connection pool across HF Hub, Cloud SSE, and Web Research.

---

## 4. UI Layer & Design System (`ui/`)
- **Theme Tokens (`ui/theme/Color.kt`, `Type.kt`, `Theme.kt`):**
  - **Dark Graphite (default):** Background `#0E1015`, Surface `#151922`, SurfaceHigh `#1C222D`, Hairline `#28303E`, Text `#EDF1F7`, TextSecondary `#94A0B4`.
  - **Light Alabaster (optional):** Background `#F4F6F9`, Surface `#FFFFFF`, SurfaceHigh `#E5EBF2`, Hairline `#D0D8E4`, Text `#0F172A`, TextSecondary `#475569`.
  - **Accents:** Cyan-Blue `#38BDF8` (`skyAccent`), Steel-Blue `#60A5FA` (`peachAccent`/Voice), Teal `#2DD4BF` (`sageAccent`/Models & Success), Amber `#F59E0B` (`sandAccent`/Device & Caution), Indigo `#818CF8` (`lavenderAccent`/Settings), Crimson `#F87171` (`coralAccent`/Error), paired with `#0B1118` (`onHue`) for high contrast.
- **Bundled Fonts (`res/font/`):** **Space Grotesk** (headlines), **Plus Jakarta Sans** (body/UI), and **JetBrains Mono** (metrics/code).
- **Navigation (`JarvisRoutes.kt`, `JarvisAppShell.kt`):**
  4 top-level destinations (`HomeRoute`, `ChatRoute`, `ModelsRoute`, `MoreRoute`) plus sub-routes (`VoiceRoute`, `DeviceRoute`, `DeviceBridgeSetupRoute`, `PermissionsSecurityRoute`, `ActivityHistoryRoute`, `SettingsRoute`), rendered with `NavigationBar` on compact screens and `NavigationRail` on expanded screens.

---

## 5. Autonomous Agent, 25 Tools & Security (`core/agent/`, `core/tools/`, `core/security/`)
- **25 Registered Tools (`ToolRegistryFactory`):**
  - App & System (`8`): `launch_installed_app`, `open_app_screen`, `go_home_screen`, `open_android_settings`, `read_battery_status`, `read_device_info`, `adjust_media_volume`, `control_media_playback`
  - Privileged Bridge (`3`): `check_device_bridge`, `force_stop_app` (`HIGH`, confirmed), `trim_app_caches` (`MEDIUM`, confirmed)
  - Comms & Reminders (`4`): `search_contacts`, `initiate_phone_call` (`HIGH`, confirmed), `compose_message` (`HIGH`, confirmed), `create_reminder` (`HIGH`, confirmed)
  - Notifications & Accessibility Fallback (`6`): `read_notifications` (sanitized), `accessibility_global_action`, `inspect_screen_elements` (sanitized + password redaction), `tap_ui_element`, `scroll_screen`, `input_ui_text`
  - Memory & Procedural Skills (`4`): `retrieve_memory`, `save_memory`, `save_skill` (high-risk auto-skills blocked), `run_skill`
- **Security Controls (`core/security/`):**
  - `SecureCredentialStore`: Android Keystore `AES/GCM/NoPadding` 256-bit key storage + `verifyKeystoreHealth()`.
  - `SecurityLogRedactor`: Scrubs Bearer tokens, API keys (`hf_*`, `AIza*`, `sk-*`), headers, and 6-digit ADB pairing codes.
  - `HttpSecuritySupport`: Strips `Authorization` headers when HTTP redirects leave `huggingface.co` / `hf.co` to third-party CDNs/S3 buckets, and bounds UTF-8 error/API responses (`<= 2 MB`).
  - `PromptSecuritySanitizer`: Isolates external screen/notification/web snippets inside `[UNTRUSTED_EXTERNAL_DATA ...]` markers.

---

## 6. Persistence Layer (`data/local/db/`)
- **Room Database v7 (`JarvisDatabase`):** WAL mode with migrations `1 → 7` across 8 tables:
  `conversations`, `messages`, `model_entries`, `download_queue`, `model_benchmarks`, `memory_notes`, `agent_executions`, `agent_skills`.
- Indexes optimize timeline/lookup queries on `messages (conversation_id, timestamp)`, `model_benchmarks (modelId, timestamp)`, `memory_notes (kind, createdAt)`, `agent_executions (timestamp)`, and `agent_skills (name)`.
