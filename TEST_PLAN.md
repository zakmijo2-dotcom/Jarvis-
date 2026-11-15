# JARVIS — Verification & Test Plan

## 1. Automated Verification Environment
- **Target SDK / Compile SDK:** Android API 36 (`minSdk = 24`)
- **Test Runner:** `Robolectric 4.16.1` + JUnit 4 + Kotlinx Coroutines Test + Jetpack Compose UI Test (`gradle :app:testDebugUnitTest`)
- **Note on Physical Device Hardware:** Hardware GPU delegate (`Mali-G720`), thermal throttling headroom, live `SpeechRecognizer` audio streams, and live Picovoice `Porcupine` microphone detection require on-device testing on the Xiaomi Poco X7 Pro and are marked accordingly when tested via fakes/Robolectric shadows.

---

## 2. Mandatory Test Scenario Matrix

| # | Scenario | Target Component | Expected Assertion | Verification Method |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **Successful Multi-Step Execution & Closed-Loop Tool Calling** | `Phase2AgentCore`, `ToolRegistry` | Model `[TOOL_CALL]` executes tool, verifies result, feeds `[TOOL_RESULT]` back, emits verified final reply. | Automated (`JarvisFoundationTest`) |
| **2** | **Failed Execution & Safe Error Recovery** | `Phase2AgentCore`, `WebResearchService` | Cloud/Web failure in `AUTO_HYBRID` recovers cleanly onto offline LiteRT-LM or emits non-fabricated notice. | Automated (`JarvisFoundationTest`) |
| **3** | **Network Disconnection (Offline Resilience)** | `IntentRouter`, `Phase2AgentCore` | Offline prompt stays on local runtime/tools without crashing or hanging on sockets. | Automated (`JarvisFoundationTest`) |
| **4** | **Model Unloaded / Unavailable** | `LiteRtLmAiEngine`, `RuntimeEngineCoordinator` | Honest `ENGINE UNLOADED` / `UnsupportedOperationException` state; never fabricates tokens. | Automated (`ExampleRobolectricTest`, `JarvisFoundationTest`) |
| **5** | **Low Memory & Concurrency Mutual Exclusion** | `RuntimeEngineCoordinator`, `ThermalAndMemoryPolicy` | Tight RAM unloads conflicting runtime cleanly and trims native heap under pressure. | Automated (`JarvisFoundationTest`) |
| **6** | **Interrupted / Cancelled Process** | `LiteRtLmAiEngine`, `Phase2AgentCore`, `ChatViewModel` | Cancellation preserves partial generated text, records fallback benchmark, stops plan without orphan coroutines. | Automated (`JarvisFoundationTest`) |
| **7** | **Permission Denied & Privacy Boundaries** | `ToolPermissionValidator`, `AccessibilityBridge`, `NotificationBridge` | Missing runtime/special permission returns `PERMISSION_DENIED` with rationale; password nodes redacted (`[REDACTED_PASSWORD]`). | Automated (`JarvisFoundationTest`) |
| **8** | **Invalid Tool Arguments & Prompt Injection Defense** | `ToolRegistry`, `PromptSecuritySanitizer` | Shell metacharacters rejected; injected `[TOOL_CALL]` inside untrusted screen/web text stripped cleanly. | Automated (`JarvisFoundationTest`) |
| **9** | **Duplicate Requests & Loop Prevention** | `DownloadQueueManager`, `Phase2AgentCore`, `RoomLocalMemory` | Duplicate downloads blocked; identical repeated agent `[TOOL_CALL]` short-circuited; duplicate memories deduplicated. | Automated (`JarvisFoundationTest`) |
| **10** | **Sensitive Action Confirmation & Safe Skill Distillation** | `CommunicationAndSystemTools`, `RoomSkillEngine` | `compose_message`, `initiate_phone_call`, `tap_ui_element`, `create_reminder` require `userConfirmed = true`; dangerous actions excluded from auto-saved skills. | Automated (`JarvisFoundationTest`) |
| **11** | **Room Migrations v1→v7 & Accessibility Verification** | `JarvisDatabase`, `AccessibilityController` | `MIGRATION_5_6` & `MIGRATION_6_7` preserve rows and add `agent_skills` + memory columns; post-action state verification detects screen change. | Automated (`JarvisFoundationTest`) |
| **12** | **UI Navigation & Permissions Dashboard** | `ExampleRobolectricTest` | All 5 tabs + `DeviceDashboardTab.PERMISSIONS` + `assistant_home_header` + Arabic RTL render cleanly. | Automated (`ExampleRobolectricTest`) |
