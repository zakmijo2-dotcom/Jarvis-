# J.A.R.V.I.S — Verification & Test Plan (v1.0.0)

## 1. Automated Verification Environment
- **Target SDK / Compile SDK:** Android API `36` (`minSdk = 24`)
- **JVM & Robolectric Runner:** `Robolectric 4.16.1` + JUnit 4 + Kotlinx Coroutines Test + Jetpack Compose UI Test (`gradle :app:testDebugUnitTest --no-daemon`)
- **Android Instrumentation Compilation:** `gradle :app:compileDebugAndroidTestKotlin --no-daemon`
- **Automated Test Count:** **55 JVM/Robolectric tests** + **4 Android Instrumentation tests** (`JarvisInstrumentationTest` + `ExampleInstrumentedTest`)
- **Physical Device Scope:** Live Shizuku daemon IPC, live Android 11+ Wireless Debugging (`127.0.0.1` SPAKE2/TLS handshake), `.litertlm` hardware GPU OpenCL execution, live `SpeechRecognizer` / Picovoice `Porcupine` microphone audio, and real Accessibility/Notification Listener OS binds require physical Android hardware and are verified via deterministic transports/fakes in JVM tests.

---

## 2. Automated Test Suite Matrix (55 JVM/Robolectric + 4 Android Instrumentation)

| Test Suite | Count | Verified Behaviors |
| :--- | :---: | :--- |
| **`DeviceControlArchitectureTest`** | **3** | Shizuku permission grant/denial & `DeadObjectException` binder-death recovery; Wireless ADB 6-digit pairing validation, code redaction (`••••••`), `id` probe gating, and disconnect transitions; `DeviceCommandRouter` priority (`Shizuku -> Wireless ADB -> Native -> Accessibility`), confirmation gating, and `AllowlistedCommandValidator` package protection. |
| **`AiRouterAndSecurityTest`** | **6** | `ModelRouter` hardware-fit scoring, measured benchmark attribution (`null` when unmeasured), severe-thermal Cloud offloading, and context capping (`2048`); >85% RAM over-budget blocking; `PromptCompressor` digest compaction; OkHttp redirect `Authorization` header stripping off `huggingface.co`; `SecurityLogRedactor` & Keystore error recording; `ModelCompatibilityEngine` context-scaled RAM checks. |
| **`UiNavigationAndSecurityTest`** | **2** | End-to-end navigation across `More -> Shizuku & Wireless ADB Setup` (pairing error banner + ephemeral 6-digit code wipe), `Permissions & Security` (Keystore health recheck), and `Activity & Benchmarks History` (3 tabs); `Model Hub` storage & hardware fit card + `Settings -> Appearance` Light/Dark Graphite theme switching. |
| **`JarvisFoundationTest`** | **37** | `Phase2AgentCore` closed-loop execution & duplicate-loop prevention; Cloud/Web fallback & offline resilience; `LiteRtLmAiEngine` & coordinator mutual exclusion; stream cancellation & partial token preservation; `ToolRegistry` (25 tools) permission/shell-blocklist/confirmation validation; `PromptSecuritySanitizer`; `DownloadQueueManager`; `RoomSkillEngine` safe distillation; `JarvisDatabase` migrations `1 -> 7`; `AccessibilityController` 5-layer verification & password redaction. |
| **`ExampleRobolectricTest`** | **6** | `HomeScreen` launch bento & `See all` history drawer; `More` hub, `Voice`, `Device`, `Models`, `Settings`, `Chat`, and Arabic RTL rendering. |
| **`ExampleUnitTest`** | **1** | Baseline JVM unit assertion sanity check. |
| **`JarvisInstrumentationTest` + `ExampleInstrumentedTest`** | **4** | *(Compiled for physical/emulator `androidTest` execution)*: Unconfigured Shizuku/Wireless ADB fail-closed checks, real `AndroidKeyStore` AES-GCM encrypt/decrypt health check, `MainActivity` multi-screen Compose navigation, and package context verification. |
