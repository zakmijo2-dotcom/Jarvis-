# J.A.R.V.I.S — Verification & Test Plan (v1.0.0)

## 1. Automated Verification Environment
- **Target SDK / Compile SDK:** Android API 36 (`minSdk = 24`)
- **Test Runner:** `Robolectric 4.16.1` + JUnit 4 + Kotlinx Coroutines Test + Jetpack Compose UI Test (`gradle :app:testDebugUnitTest --no-daemon`)
- **Automated Test Count:** **44 passing tests** (`1` in `ExampleUnitTest`, `37` in `JarvisFoundationTest`, `6` in `ExampleRobolectricTest`)
- **Physical Device Requirements:** Hardware GPU delegate (`OpenCL`), live thermal throttling callbacks, physical microphone `SpeechRecognizer` streams, live Picovoice `Porcupine` wake-word spotting, and real Android Accessibility Service interactions require testing on a physical Android device and are represented via deterministic fakes/shadows in JVM/Robolectric tests.

---

## 2. Automated Test Scenario Matrix (44 Tests)

| # | Scenario | Target Component | Expected Assertion | Verification Method |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **Successful Multi-Step Execution & Closed-Loop Tool Calling** | `Phase2AgentCore`, `ToolRegistry` | Model `[TOOL_CALL]` executes tool, verifies result, feeds `[TOOL_RESULT]` back, and emits verified final reply. | Automated (`JarvisFoundationTest`) |
| **2** | **Failed Execution & Safe Error Recovery** | `Phase2AgentCore`, `WebResearchService` | Cloud/Web failure in `AUTO_HYBRID` recovers cleanly onto offline LiteRT-LM or emits a non-fabricated notice. | Automated (`JarvisFoundationTest`) |
| **3** | **Network Disconnection (Offline Resilience)** | `IntentRouter`, `Phase2AgentCore` | Offline prompt stays on local runtime/tools without crashing or hanging on sockets. | Automated (`JarvisFoundationTest`) |
| **4** | **Model Unloaded / Unavailable** | `LiteRtLmAiEngine`, `RuntimeEngineCoordinator` | Honest `Unloaded` / `UnsupportedOperationException` state; never fabricates tokens. | Automated (`ExampleRobolectricTest`, `JarvisFoundationTest`) |
| **5** | **Low Memory & Concurrency Mutual Exclusion** | `RuntimeEngineCoordinator`, `ThermalAndMemoryPolicy` | Tight RAM unloads conflicting runtime cleanly and trims native heap under pressure. | Automated (`JarvisFoundationTest`) |
| **6** | **Interrupted / Cancelled Process** | `LiteRtLmAiEngine`, `Phase2AgentCore`, `ChatViewModel` | Cancellation preserves partial generated text, records fallback benchmark, and stops plan without orphan coroutines. | Automated (`JarvisFoundationTest`) |
| **7** | **Permission Denied & Privacy Boundaries** | `ToolPermissionValidator`, `AccessibilityBridge`, `NotificationBridge` | Missing runtime/special permission returns `PERMISSION_DENIED` with rationale; password nodes redacted (`[REDACTED_PASSWORD]`). | Automated (`JarvisFoundationTest`) |
| **8** | **Invalid Tool Arguments & Prompt Injection Defense** | `ToolRegistry`, `PromptSecuritySanitizer` | Shell metacharacters rejected; injected `[TOOL_CALL]` inside untrusted screen/web text stripped cleanly. | Automated (`JarvisFoundationTest`) |
| **9** | **Duplicate Requests & Loop Prevention** | `DownloadQueueManager`, `Phase2AgentCore`, `RoomLocalMemory` | Duplicate downloads blocked; identical repeated agent `[TOOL_CALL]` short-circuited; duplicate memories deduplicated. | Automated (`JarvisFoundationTest`) |
| **10** | **Sensitive Action Confirmation & Safe Skill Distillation** | `CommunicationAndSystemTools`, `RoomSkillEngine` | `compose_message`, `initiate_phone_call`, `tap_ui_element`, and `create_reminder` require `userConfirmed = true`; dangerous actions excluded from auto-saved skills. | Automated (`JarvisFoundationTest`) |
| **11** | **Room Migrations v1→v7 & Accessibility Verification** | `JarvisDatabase`, `AccessibilityController` | `MIGRATION_1_2` through `MIGRATION_6_7` preserve rows and add `agent_skills` + memory lifecycle columns; post-action state verification detects screen change. | Automated (`JarvisFoundationTest`) |
| **12** | **Home Bento, 4-Tab Navigation, Drawer & Arabic RTL** | `ExampleRobolectricTest` | Launches on `HomeScreen` (greeting, Bento tiles, `See all` → Chat + History drawer), navigates across `More` (`Voice`, `Device`, `Settings`), `Models` (`Discover`, `Queue`, `Benchmarks`), and `Chat` (including Arabic RTL rendering). | Automated (`ExampleRobolectricTest`) |
