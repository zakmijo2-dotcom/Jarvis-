# Open-Source Credits, Attribution & GPL-3.0 License Compliance (v1.0.0)

## 1. Project License
**J.A.R.V.I.S Android (`v1.0.0`)** is distributed under the **GNU General Public License v3.0 (`GPL-3.0`)**. See [`LICENSE`](LICENSE) at the repository root for the complete license text.

---

## 2. Third-Party Libraries & Architectural Inspiration Matrix

All third-party dependencies linked by J.A.R.V.I.S use permissive licenses (**Apache License 2.0** or **MIT License**) that are compatible with **GPL-3.0** distribution.

| Project / Library | Repository / Artifact | Version | License | Strategy | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Google LiteRT-LM** | `com.google.ai.edge.litertlm:litertlm-android` | `0.10.2` | **Apache License 2.0** | **DIRECT DEPENDENCY** | On-device `.litertlm` LLM inference engine with GPU OpenCL (`Backend.GPU`) and CPU XNNPACK (`Backend.CPU`) execution. |
| **Picovoice Porcupine** | `ai.picovoice:porcupine-android` | `4.0.2` | **Apache 2.0 SDK + Picovoice Console Key Terms** | **DIRECT DEPENDENCY** | Built-in `"Jarvis"` wake-word spotting behind a user-supplied Picovoice AccessKey. |
| **AndroidX Jetpack Compose / Room / Navigation / DataStore** | `androidx.*` | BOM `2024.09.00` / Room `2.7.0` / Nav `2.8.9` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Material 3 UI, reactive `StateFlow` lifecycle binding, type-safe navigation routes, and Room v7 WAL SQLite persistence. |
| **Kotlinx Coroutines & Serialization** | `org.jetbrains.kotlinx:*` | Coroutines `1.10.2` / Serialization `1.8.1` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Structured concurrency, reactive streams, and JSON serialization. |
| **Square OkHttp** | `com.squareup.okhttp3:okhttp` | `4.10.0` | **Apache License 2.0** | **DIRECT DEPENDENCY** | Connection-pooled HTTP client for Hugging Face Hub, HTTP 206 resumable downloads, Cloud AI SSE, and Web Research. |
| **Google Fonts (Inter & JetBrains Mono)** | Bundled `.ttf` in `app/src/main/res/font/` | Static TTF | **SIL Open Font License 1.1 (OFL-1.1)** | **BUNDLED FONT ASSETS** | Bundled locally for offline UI typography (`Inter`) and code/telemetry display (`JetBrains Mono`). |
| **Robolectric & JUnit** | `org.robolectric:robolectric`, `junit:junit` | `4.16.1` / `4.13.2` | **MIT / EPL-1.0** | **TEST-ONLY DEPENDENCY** | Used only in `testImplementation` for local JVM and Compose UI verification. |
| **Autonomous Agent & UI Automation Concepts** | `AbuZar-Ansarii/Jarvis`, `wangzan101/Mika`, `JMAN730/opendroid`, `NITISH-R-G/ScreenSaathi` | — | **MIT / Apache 2.0** | **CONCEPTUAL INSPIRATION** | Inspired the Observe → Think → Act → Verify closed-loop agent design, workflow skill distillation, and structured UI Accessibility node verification. All Kotlin implementations in this repository were written from scratch for Android Room v7, `ToolRegistry`, and Jetpack Compose. |
