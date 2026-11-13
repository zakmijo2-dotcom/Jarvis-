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
- **Wake-Word Boundary:** Modular `AudioRecord` PCM energy-gate interface targeting `"Jarvis"`; explicitly reports `KWS UNVERIFIED` until a trained keyword model/key is linked.
- **Secure Tool & Hybrid Execution:** Permission-validated Android tools (app launcher, battery, hardware telemetry, media volume/playback, contacts search, dial/SMS confirmation gates, opt-in Notification Listener & Accessibility navigation), multi-step intent planning, local Room memory, configurable Web Research, and Keystore AES-256-GCM encrypted API keys.

### UI Polish, Localization & Security Hardening
- Smooth `NavHost` crossfade transitions, adaptive Bottom Bar / Navigation Rail (`>=600dp`), and zero-frame static `JarvisOrb` when animation is disabled.
- Automatic per-message RTL detection for Arabic script alongside English (`values`), Arabic (`values-ar`), and French (`values-fr`) UI resources.
- R8 minification & resource shrinking enabled for release builds; `android:allowBackup="false"` with explicit exclusions in `backup_rules.xml` and `data_extraction_rules.xml`.
