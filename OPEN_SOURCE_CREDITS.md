# Open-Source Credits, Attribution & GPL-3.0 License Compliance (v1.0.1)

## 1. Project License
**J.A.R.V.I.S Android (`v1.0.1`)** is distributed under the **GNU General Public License v3.0 (`GPL-3.0`)**. See [`LICENSE`](LICENSE) at the repository root for the complete license text.

---

## 2. Third-Party Libraries & Architectural Attribution Matrix

All third-party dependencies linked by J.A.R.V.I.S use permissive licenses (**Apache License 2.0**, **MIT License**, or **SIL OFL 1.1**) that are compatible with **GPL-3.0** distribution.

| Project / Library | Repository / Artifact | Version | License | Strategy | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Shizuku API & Provider** | `dev.rikka.shizuku:api`, `dev.rikka.shizuku:provider` | `13.1.5` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Official Shizuku binder IPC, permission flow, and privileged `UserService` execution (`IJarvisShizukuService`). |
| **Kadb (Pure-Kotlin ADB)** | `com.flyfishxu:kadb` (via JitPack / Maven) | `1.3.0` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Android 11+ Wireless Debugging SPAKE2 pairing and TLS 1.3 ADB client without bundled native binaries. |
| **Google LiteRT-LM** | `com.google.ai.edge.litertlm:litertlm-android` | `0.10.2` | **Apache License 2.0** | **DIRECT DEPENDENCY** | On-device `.litertlm` LLM inference engine with GPU OpenCL (`Backend.GPU`) and CPU XNNPACK (`Backend.CPU`) execution. |
| **Google ML Kit Text Recognition** | `com.google.mlkit:text-recognition` | `16.0.1` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Offline on-device Latin OCR (`OnDeviceOcrEngine`) fallback when Accessibility hierarchy nodes are sparse or custom-drawn. |
| **Picovoice Porcupine** | `ai.picovoice:porcupine-android` | `4.0.2` | **Apache 2.0 SDK + Picovoice Console Key Terms** | **DIRECT DEPENDENCY** | Built-in `"Jarvis"` wake-word spotting behind a user-supplied Picovoice AccessKey. |
| **AndroidX Jetpack Compose / Room / Navigation / DataStore** | `androidx.*` | BOM `2024.09.00` / Room `2.7.0` / Nav `2.8.9` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Material 3 UI, reactive `StateFlow` lifecycle binding, `@Serializable` navigation routes, and Room v8 WAL SQLite persistence. |
| **Kotlinx Coroutines & Serialization** | `org.jetbrains.kotlinx:*` | Coroutines `1.10.2` / Serialization `1.8.1` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Structured concurrency, cancellation, reactive streams, and JSON serialization. |
| **Square OkHttp** | `com.squareup.okhttp3:okhttp` | `4.10.0` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Connection-pooled HTTP client with 16 MB disk cache for Hugging Face Hub, HTTP 206 resumable downloads, Cloud AI SSE, Web Research, and MCP JSON-RPC 2.0. |
| **Google Fonts (Space Grotesk, Plus Jakarta Sans & JetBrains Mono)** | Bundled `.ttf` in `app/src/main/res/font/` | Static TTF | **SIL Open Font License 1.1 (OFL-1.1)** | **BUNDLED FONT ASSETS** | Bundled locally for offline editorial headers (`Space Grotesk`), UI copy (`Plus Jakarta Sans`), and telemetry/code (`JetBrains Mono`). |
| **Robolectric, JUnit & AndroidX Test** | `org.robolectric:robolectric`, `junit:junit`, `androidx.test.*` | `4.16.1` / `4.13.2` / `1.6.x` | **MIT / EPL-1.0 / Apache 2.0** | **TEST-ONLY DEPENDENCY** | Used in `testImplementation` and `androidTestImplementation` for headless JVM, Robolectric UI, and Compose instrumentation tests. |
| **OpenPhoneUse** (`joungminsung/openphoneuse`) | `c980cbf134a5d5eb1ec6cd097fcfabc45d6a73` | `main` | **MIT License** | **CLEAN-ROOM ARCHITECTURE** | Inspired `ScreenTreeCompressor`, `ScreenJournal` loop detection, `ScreenRiskClassifier`, and `TYPE_ACCESSIBILITY_OVERLAY` `ExecutionOverlayManager`. Zero copied files. |
| **Juyu Phone Agent** (`goehou/Juyu-phone-agent`) | `7810db36e2b030d0bd55c91297b106314a4caad7` | `main` | **Apache License 2.0** | **CLEAN-ROOM ARCHITECTURE** | Inspired API 30+ `AccessibilityService.takeScreenshot`, Base64 JPEG compression, multimodal attachments, and Streamable HTTP `McpClient`. Zero copied files. |
| **OpenRing** (`SunZhi-Will/OpenRing`) | `b61094ab9e427d14913e61cea194fe9f4cee43cb` | `main` | **MIT License** | **CLEAN-ROOM ARCHITECTURE** | Inspired `MediaProjectionForegroundService` + fallback frame capture and multi-strategy Accessibility input. QuickJS deliberately omitted. |
| **Zafiro** (`niki914/zafiro`) | `275ce1bd41949cc4ac3fc71715aef119b0469ac1` | `main` | **MIT License** | **CLEAN-ROOM ARCHITECTURE** | Inspired DFS child culling, `AccessibilityNodeInfo.recycle()` hygiene, `ContextPruner` observation trimming, and `McpManager` separation. |
| **AndroidHarness** (`Sanuu7/androidharness`) | `28ceacb04ee4ea4117cc46d417e49be3304dc085` | `main` | **MIT License** | **CLEAN-ROOM ARCHITECTURE** | Inspired `PublicHttpSafetyGuard` SSRF protection, Brave/SerpAPI/Keyless search backends, `WebPageExtractor`, and Room `agent_checkpoints` resume. |
| **OpenPhone** (`secondly-com/openphone`) | `b4b6e76fc353bac249a9877cdda9722e7b3f3db8` | `main` | **OpenPhone Mixed / PolyForm Noncommercial 1.0.0** | **POLICY REFERENCE ONLY (STRICTLY EXCLUDED CODE)** | Studied conceptual permission/audit policy framing only. **Zero source files, patches, or dependencies included in JARVIS.** |
