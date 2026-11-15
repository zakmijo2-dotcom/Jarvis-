# Open-Source Credits, Integration Matrix & License Compliance

## 1. Competitor & Open-Source Integration Matrix

| Project / Library | Repository / Artifact | Version | License | Strategy | Compatibility & Decision Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **JARVIS (Competitor Reference)** | `https://github.com/AbuZar-Ansarii/Jarvis` | v1.1.0+ | **MIT License** | **ADAPTATION & INSPIRED REIMPLEMENTATION** | **Adapted:** Hermes-style workflow skill distillation (reimplemented natively in Room SQLite with strict dangerous-action exclusion and Android permission validation) and structured UI Accessibility node observation/verification. **Rejected:** External Ollama server assumption, passive sensor watchdogs (battery impact), unauthenticated background scraping. |
| **Google LiteRT-LM** | `com.google.ai.edge.litertlm:litertlm-android` | `0.10.2` | **Apache License 2.0** | **DIRECT REUSE** | Active primary local LLM engine with Mali-G720 GPU (`Backend.GPU`) and XNNPACK CPU (`Backend.CPU`) fallback. |
| **Picovoice Porcupine** | `ai.picovoice:porcupine-android` | `4.0.2` | **Apache 2.0 SDK + Picovoice Console Key Terms** | **DIRECT REUSE** | Used for built-in `"Jarvis"` keyword spotting behind user-supplied AccessKey (`BLOCKED BY EXTERNAL REQUIREMENT` until user enters key). |
| **AndroidX Jetpack Compose / Room / Navigation / DataStore** | `androidx.*` | BOM `2024.09.00` / Room `2.7.0` / Nav `2.8.9` | **Apache License 2.0** | **DIRECT REUSE** | Core UI, reactive StateFlow persistence, type-safe routes, WAL SQLite DB. |
| **Square OkHttp** | `com.squareup.okhttp3:okhttp` | `4.10.0` | **Apache License 2.0** | **DIRECT REUSE** | Connection-pooled networking for Hugging Face Hub, HTTP 206 resume, Cloud AI SSE, and Web Research. |
| **k2-fsa sherpa-onnx** | `https://github.com/k2-fsa/sherpa-onnx` | `2.0.1` | **Apache License 2.0** | **EVALUATED / DEFERRED** | Compatible offline ONNX KWS/ASR option; deferred from bundling in APK to keep binary lean while `Porcupine` + Android `SpeechRecognizer` handle active voice without bloating APK size by native `.so` archives. |
| **Mika / OpenDroid / ScreenSaathi** | `wangzan101/Mika`, `JMAN730/opendroid`, `NITISH-R-G/ScreenSaathi` | - | Open Source (MIT/Apache) | **INSPIRED REIMPLEMENTATION** | Reinforced bounded Observe→Think→Act→Verify closed-loop UI automation design with explicit confirmation for consequential taps/inputs. |
| **Robolectric & AndroidX Test** | `org.robolectric:robolectric` | `4.16.1` | **MIT / Apache 2.0** | **DIRECT REUSE** | Headless JVM UI, Room migration, and Accessibility workflow verification. |

---

## 2. Attribution & License Notices

### MIT License (Concepts Adapted from `AbuZar-Ansarii/Jarvis`)
> Inspiration for modular macro-skill distillation from multi-step workflows and structured UI element tree inspection is credited to `AbuZar-Ansarii/Jarvis` (MIT License). All implementations in `com.example.core.skills`, `com.example.core.tools.AccessibilityController`, and `com.example.core.agent.Phase2AgentCore` were written natively in Kotlin for Room v7, ToolRegistry, and Material 3 without copying source files verbatim.

### Apache License 2.0 Libraries
All AndroidX libraries, Kotlinx Coroutines/Serialization, Square OkHttp, and Google LiteRT-LM dependencies are used in full compliance with Apache License 2.0. No GPL/AGPL or proprietary code has been incorporated.
