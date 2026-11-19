# J.A.R.V.I.S — Changelog

All notable changes for **J.A.R.V.I.S Android** are documented in this file.  
License: **GNU General Public License v3.0 (`GPL-3.0`)**

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
