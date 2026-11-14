# JARVIS Android — Release Notes (v1.0.0)

**Target Device Profile:** Xiaomi Poco X7 Pro (MediaTek Dimensity 8400-Ultra, 12 GB RAM, Android 15/16, HyperOS)  
**Application ID:** `com.aistudio.jarvis.k8v4mz`  
**Version:** `1.0.0` (versionCode `1`)

## Highlights in v1.0.0 (Phase 7 Production Release)

### Local AI & Hardware Optimization
- **LiteRT-LM On-Device Inference (`0.10.2`):** Supports Gemma 3 1B Instruct (INT4) with GPU OpenCL default and automatic CPU XNNPACK fallback.
- **Memory-Aware Guard:** Checks live `ActivityManager.MemoryInfo` before native weight allocation; blocks unsafe model initialization under critical OS low-memory pressure (`<384 MB` free).
- **Thermal Adaptation:** Monitors `PowerManager.currentThermalStatus`, dynamically capping output budgets (`<=384` tokens at Moderate thermal state, `<=256` tokens at Severe+) and preferring CPU fallback under Critical thermal throttling.
- **OS Memory Lifecycle:** `ComponentCallbacks2` (`onTrimMemory`) releases inactive KV-cache conversation contexts when backgrounded and unloads idle native engines on `TRIM_MEMORY_COMPLETE`.
- **SQLite WAL & Lazy DI:** Room v5 runs with `WRITE_AHEAD_LOGGING`; `JarvisApplication` initializes dependencies lazily and reuses a shared `OkHttpClient` connection pool.

### Real Benchmark & Telemetry Dashboard
- Persists 7 measured dimensions per inference run in Room (`model_benchmarks` v5):
  1. Model initialization time (`ms`)
  2. Time to first token (`TTFT ms`)
  3. Decode throughput (`tokens/sec`)
  4. Native heap & peak memory (`MB`)
  5. Model artifact file size
  6. Inference backend used (GPU OpenCL, CPU XNNPACK, or Cloud SSE)
  7. Live thermal state, battery percentage, and battery temperature (`°C`)

### Voice, Device Control & Hybrid Agent
- **Multilingual Voice Console:** Real Android `SpeechRecognizer` & `TextToSpeech` supporting Arabic, Algerian Arabic (`ar-DZ`), French (`fr-FR`), and English (`en-US`), with Push-to-Talk, Continuous Loop (Foreground Service), text fallback, and Markdown/URL sanitization.
- **Native Porcupine Wake-Word Integration (`4.0.2`):** Integrates `ai.picovoice:porcupine-android:4.0.2` (`BuiltInKeyword.JARVIS`) with a speech-band RMS/ZCR pre-filter, 2.5s cooldown debouncer, and Keystore-encrypted AccessKey configuration. Strictly reports `UNAVAILABLE_PENDING_MODEL_OR_KEY` (`verified=false`) until a valid AccessKey is provided and real PCM frames are processed.
- **17-Tool Android Automation & On-Demand Accessibility Suite:** Adds `inspect_screen_elements`, `tap_ui_element`, `scroll_screen`, and `input_ui_text` alongside existing app, hardware, media, contact, call, SMS, and notification tools. `onAccessibilityEvent` remains a strict no-op (zero passive scraping), password nodes are redacted, and all high-risk tools require explicit user confirmation.
- **Multi-Runtime Coordinator & Room v6 WAL:** Coordinates `LiteRT-LM` alongside prepared runtime adapters (`GGUF`, `TFLite`, `MediaPipe`) with single-heavy-model mutual exclusion on tight RAM, full tool-catalog injection into both local and cloud prompts, and `MIGRATION_5_6` preserving custom Hugging Face context windows.

### Realistic Obsidian Slate UI Overhaul & Security Hardening
- **Obsidian Slate & Titanium Palette:** Replaces heavy neon HUD borders with matte obsidian slate (`#090D14` / `#0E141F` / `#131B29`), 1dp structural hairlines (`#222F44`), brushed titanium (`#94A3B8`), and restrained cyan (`#22D3EE`).
- **Unified Assistant Home & Rich Chat Actions:** Adds a unified assistant status header with interactive `JarvisOrb` and live runtime telemetry above chat, fenced monospace code block rendering with one-tap **Copy Code**, message-level **Copy**, and **Regenerate Response**.
- **Security Hardening:** R8 minification & resource shrinking enabled (`proguard-rules.pro` updated for LiteRT-LM and Porcupine JNI); `android:allowBackup="false"` with explicit exclusions in `backup_rules.xml` and `data_extraction_rules.xml`.

