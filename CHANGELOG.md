# J.A.R.V.I.S — Changelog

All notable changes for **J.A.R.V.I.S Android** are documented in this file.  
License: **GNU General Public License v3.0 (`GPL-3.0`)**

---

## [1.0.2] — Autonomous Agent Core, Browser Automation, App Knowledge, Visual Identity & Repo Cleanup (`versionCode = 3`)

### Added & Upgraded
- **Autonomous Goal Planning & Closed-Loop Orchestration (`core/agent/`, `core/background/`):**
  - Added `GoalTaskPlanner` for multi-step goal decomposition (`MEMORY_LOOKUP -> ANDROID_TOOL -> CONTEXT_OBSERVE -> AI_SYNTHESIS -> RESULT_VERIFY`) with explicit URL precedence over incidental app-name collisions.
  - Upgraded `Phase2AgentCore` with explicit `AgentExecutionPhase` states, structured `AgentTimelineEntry` logs, bounded dynamic replanning (maximum 3 replans per run), and 5 new `HybridAgentEvent` variants (`PhaseChanged`, `TimelineRecorded`, `ObservationCaptured`, `ReplanTriggered`, `TaskStopped`).
  - Added `AgentBackgroundCoordinator`, `AgentExecutionForegroundService` (live Pause / Resume / Stop notification controls, keyguard lock-state safety checks, interrupted checkpoint recovery), and `VirtualDisplayFeasibilityProbe`.
- **Unified Automation, Vision Classification & Stateful Browser Engine (`core/automation/`, `core/vision/`, `core/browser/`, `core/tools/`):**
  - Added `PhoneAutomationEngine` (`AndroidAutomationEngine`) and registered `wait_for_screen`, `locate_and_tap_visual`, and `navigate_device`.
  - Added `ScreenClassifier` (9 screen types), `VisualElementLocator` (hybrid Accessibility + OCR matching), and context-aware cloud screenshot gating (`ScreenPerceptionEngine.shouldAttachScreenshotToCloud`).
  - Added stateful multi-tab `BrowserAutomationEngine` guarded by `PublicHttpSafetyGuard` (SSRF protection) and registered 6 browser tools (`browser_open`, `browser_read`, `browser_click_link`, `browser_fill_form`, `browser_back`, `browser_tabs`), bringing `ToolRegistry` to **43 registered tools**.
  - Added `ModelCapabilityDetector` for capability-aware tiered cloud model selection.
- **Application Knowledge Base, Skills JSON Exchange & Room Database v9 (`core/memory/`, `core/skills/`, `data/local/db/`):**
  - Upgraded `JarvisDatabase` cleanly from v8 to **v9** via deterministic `MIGRATION_8_9` (**12** total tables), adding `app_knowledge` (`AppKnowledgeRepository`) with sensitive-data and blocked-package safeguards.
  - Added Skills Library search filtering plus JSON Export (with clipboard copy) and JSON Import in `SettingsScreen`, along with an interactive **App Knowledge Base** management section.
- **Visual Identity, Design System & Autonomous Agent UI (`ui/`, `res/`):**
  - Added centralized design tokens (`DesignTokens.kt`), `JarvisDesignSystem.kt` (`JarvisGlassCard`, `JarvisStatusIndicator`, `JarvisSectionHeader`, `JarvisEmptyStateCard`, memoized `JarvisMarkdownText`), and `AgentActivityComponents.kt` (`AgentPhaseBadge`, `AgentLiveTimelineSection`, `ScreenObservationPreviewCard`, `AgentStopSummaryBanner`).
  - Upgraded `ChatScreen` and `HomeScreen` with live `AgentPhaseBadge`, Pause / Resume / Steer / Stop controls, Replan count pill, collapsible live tool timeline, observation preview card, stop summary banner, interrupted checkpoint notices, and an inline Quick Autonomous Task launcher.
  - Replaced default/legacy branding with an original J.A.R.V.I.S adaptive launcher icon, 5-density mipmap set, `docs/assets/jarvis-logo.png` (512×512), and `docs/assets/jarvis-banner.png` (1280×640).
- **Repository Cleanup, Open-Source Governance & Verification:**
  - Removed temporary `/research` workspace clutter, obsolete scratch files, unused fonts/drawables, and dead theme wrappers; organized technical documentation cleanly under `/docs/`.
  - Added `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `.github/ISSUE_TEMPLATE/*`, and `.github/PULL_REQUEST_TEMPLATE.md`.
  - Added `AutonomousAgentUpgradeTest` (**11 tests**) and upgraded Room migration & tool registry assertions; verified **72 / 72** passing JVM & Robolectric tests across **8 suites** (`0` failures, `0` errors, `0` skipped).

---

## [1.0.1] — Autonomous Non-Root Phone-Use, Vision/OCR, MCP, SSRF Guard & Dual-Mode UI (`versionCode = 2`)

### Added & Upgraded
- **Autonomous Non-Root Phone-Use & Offline Vision (`core/vision/`, `core/tools/`, `core/overlay/`):**
  - Integrated clean-room `ScreenTreeCompressor` (indexed `(cx, cy)` bounds, password `[REDACTED_PASSWORD]` masking, volatile digit stripping) and `ScreenJournal` (stagnation / `A-B-A-B` loop detection injecting `[RECOVERY_SIGNAL]`).
  - Added offline on-device OCR via Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`), API 30+ `AccessibilityService.takeScreenshot()`, and Android 14+ `MediaProjectionForegroundService` capture fallback (`MediaProjectionCaptureController`).
  - Registered `capture_screen_ocr`, `capture_screen_visual`, `tap_coordinates`, `long_press_coordinates`, `swipe_screen`, `open_url`, `fetch_webpage`, and `mcp_call`, bringing `ToolRegistry` to **34 registered tools**.
  - Added `ExecutionOverlayManager` (`TYPE_ACCESSIBILITY_OVERLAY`) with automatic capture hiding and instant Stop controls.
- **Safety Matrix, Checkpoints & Closed-Loop Upgrades (`core/security/`, `core/agent/`):**
  - Added `ScreenRiskClassifier` and `AppBlocklistPolicy` blocking automated control/screenshot upload inside sensitive banking, authenticator, and package installer apps.
  - Upgraded `Phase2AgentCore` to a bounded **12-iteration** closed loop with mid-run user steering (`submitSteeringMessage`), instant cancellation, missing-node recovery, and Room `agent_checkpoints` persistence & resume.
  - Upgraded `OkHttpCloudAiClient` with consented multimodal image attachments (`CloudImageAttachment`) across Gemini, OpenAI/OpenRouter, and Anthropic.
- **SSRF-Safe Web Research & Streamable HTTP/SSE MCP (`core/web/`, `core/mcp/`):**
  - Added `PublicHttpSafetyGuard` (blocking loopback, RFC-1918 LAN, link-local / cloud metadata `169.254.169.254`, CGNAT, and IPv6 ULA across requests and redirects), `WebPageExtractor`, Brave/SerpAPI backends, and `research_history`.
  - Added `McpClient` + `McpManager` supporting Streamable HTTP + SSE JSON-RPC 2.0 (`initialize`, `notifications/initialized`, `tools/list`, `tools/call`, `Mcp-Session-Id`) with write-tool confirmation gates and Room `mcp_servers`.
- **Dual-Mode UX & Room Database v8 (`ui/`, `data/local/db/`):**
  - Shipped **Clean Chat Default Mode** (`uiAdvancedModeEnabled = false`) alongside opt-in **Advanced Operator Mode** (`true`) exposing all 4 workspace tabs and operator sub-hubs.
  - Added `ActivityResultContracts.PickVisualMedia` photo attachment picker in Chat (zero broad storage permissions).
  - Upgraded `JarvisDatabase` cleanly from v7 to **v8** via deterministic `MIGRATION_7_8` (`11` total tables).
- **Verification & Build:**
  - Verified **61 / 61** JVM & Robolectric unit tests across 7 suites (`AutonomousPhoneUseTest` added cleanly) plus **4** compiled `androidTest` instrumentation tests.
  - Verified R8-optimized signed Release APK (`JARVIS-v1.0.1.apk`, **41.88 MB** / `43,913,895 bytes`) and fast signed Release APK (`56 MB`).

---

## [1.0.0] — Major Architecture Upgrade & Release (`versionCode = 1`)

### Added & Upgraded
- **Shizuku-First & Pure-Kotlin Wireless ADB Architecture (`core/device/`, `aidl/`):**
  - Integrated official **Shizuku API & Provider (`13.1.5`)** with binder received/dead listeners, permission request & denial handling, and bound `JarvisShizukuUserService` (`IJarvisShizukuService.aidl`).
  - Integrated **pure-Kotlin Wireless ADB (`com.flyfishxu:kadb:1.3.0`)** implementing Android 11+ SPAKE2 + TLS 1.3 pairing/connection, `NsdManager` mDNS port discovery, live `id` verification probes, and immediate wiping/redaction of 6-digit pairing codes.
  - Built unified capability-based `DeviceCommandRouter` (`Shizuku -> Wireless ADB -> Native Android -> Accessibility fallback`) with explicit risk metadata, confirmation gates, and outcome verification (`pidof`, exit codes, binder checks).
- **AI Model Engineering, Honest Format Guards & `ModelRouter` (`core/ai/`, `core/model/`):**
  - Hardened `RuntimeFormatAdapter` & `ModelCompatibilityEngine` so unbundled formats (`GGUF`, `TFLite`, `MediaPipe .task`, `Safetensors`) explicitly report `isExecutionSupported = false`.
  - Added hardware-, thermal-, battery-, and benchmark-aware `ModelRouter` and `PromptCompressor`, plus a shared **16 MB OkHttp disk cache** (`http_api_cache`).
- **Dark Graphite & Cyan-Blue + Light Mode UI Across 9 Screens (`ui/`):**
  - Added **Dark Graphite** default + **Light Alabaster** toggle, bundled **Space Grotesk**, **Plus Jakarta Sans**, and **JetBrains Mono** fonts, and AGSL `JarvisOrb`.
- **Security Hardening & CI Fix:**
  - Added `SecurityLogRedactor`, `HttpSecuritySupport.hfRedirectAuthSanitizer`, `SecureCredentialStore.verifyKeystoreHealth()`, R8 rules, and `.github/workflows/android-release.yml`.
