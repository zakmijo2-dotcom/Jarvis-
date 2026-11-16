# J.A.R.V.I.S — Changelog

All notable changes for **J.A.R.V.I.S Android** are documented in this file.  
License: **GNU General Public License v3.0 (`GPL-3.0`)**

---

## [1.0.2] — Autonomous Phone Use, MCP & Screen Perception (`versionCode = 3`)

### Added
- **Autonomous Phone Use Suite (`core/tools/PhoneUseTools.kt`):**
  - Full mobile action orchestration including smart coordinates tapping, text injection, gestures, and scrolling.
  - Multi-step phone use workflow execution with state change validation.
- **Real-Time Screen Perception & Vision (`core/vision/`):**
  - Integrated `MediaProjectionCaptureController` and foreground service for on-demand screen frames.
  - On-device text recognition with `OnDeviceOcrEngine`.
  - Structural UI accessibility tree compression (`ScreenTreeCompressor`) and temporal screen journal (`ScreenJournal`).
- **Model Context Protocol (MCP) Client (`core/mcp/`):**
  - Implemented `McpClient` and `McpManager` supporting standard JSON-RPC 2.0 tool discovery and execution.
- **Execution Overlay (`core/overlay/ExecutionOverlayManager`):**
  - High-visibility system overlay indicating active AI phone use with instant stop control.
- **Web Content Extraction & HTTP Security (`core/web/`):**
  - Implemented `WebPageExtractor` for clean HTML-to-Markdown processing.
  - Added `PublicHttpSafetyGuard` with strict SSRF protection blocking private and local IP ranges.
- **Security App Blocklist & Risk Classification (`core/security/`):**
  - Sensitive application protection via `AppBlocklistPolicy`.
  - Visual and input field risk assessment with `ScreenRiskClassifier`.
- **Automated CI/CD Release Pipeline:**
  - Fast signed compilation for `Jarvis-release.apk` and `Jarvis-debug.apk` with automatic GitHub Releases asset deployment.

---

## [1.0.0] — Major Architecture Upgrade & Release (`versionCode = 1`)

### Added & Upgraded
- **Shizuku-First & Pure-Kotlin Wireless ADB Architecture (`core/device/`, `aidl/`):**
  - Integrated official **Shizuku API & Provider (`13.1.5`)** with binder received/dead listeners, permission request & denial handling, and bound `JarvisShizukuUserService` (`IJarvisShizukuService.aidl`).
  - Integrated **pure-Kotlin Wireless ADB (`com.flyfishxu:kadb:1.3.0`)** implementing Android 11+ SPAKE2 + TLS 1.3 pairing/connection, `NsdManager` mDNS port discovery, live `id` verification probes, and immediate wiping/redaction of 6-digit pairing codes.
  - Built unified capability-based `DeviceCommandRouter` (`Shizuku -> Wireless ADB -> Native Android -> Accessibility fallback`) with explicit risk metadata, confirmation gates, and outcome verification (`pidof`, exit codes, binder checks).
  - Registered 3 privileged bridge tools (`check_device_bridge`, `force_stop_app`, `trim_app_caches`), bringing `ToolRegistry` to **25 allowlisted Android tools**.
- **AI Model Engineering, Honest Format Guards & `ModelRouter` (`core/ai/`, `core/model/`):**
  - Hardened `RuntimeFormatAdapter` & `ModelCompatibilityEngine` so unbundled formats (`GGUF`, `TFLite`, `MediaPipe .task`, `Safetensors`) explicitly report `isExecutionSupported = false` with downloads/execution blocked—eliminating misleading compatibility badges.
  - Added hardware-, thermal-, battery-, and benchmark-aware `ModelRouter` enforcing RAM load budgets (`<= 85%` total RAM, `>= 2 GB` concurrent reserve), context capping under tight RAM/thermal strain, and automatic thermal offloading in `AUTO_HYBRID`.
  - Added `PromptCompressor` for deterministic recent-turn preservation and older-turn context compaction, plus a shared **16 MB OkHttp disk cache** (`http_api_cache`) and `JarvisApplication` cold-startup timing telemetry.
- **Dark Graphite & Cyan-Blue + Light Mode UI Across 9 Screens (`ui/`):**
  - Redesigned theme (`Color.kt`, `Type.kt`, `Theme.kt`) with **Dark Graphite** (`#0E1015` / `#151922` / `#1C222D`) default + **Light Alabaster** (`#F4F6F9` / `#FFFFFF`) toggle, restrained cyan-blue/steel-blue/teal/amber/indigo accents, and bundled **Space Grotesk**, **Plus Jakarta Sans**, and **JetBrains Mono** fonts.
  - Added and redesigned all 9 user journeys across a 4-tab shell (`Home`, `Chat`, `Models`, `More`) plus dedicated sub-screens: `Voice`, `Device Dashboard`, `Shizuku & Wireless ADB Setup`, `Permissions & Security`, `Activity & Benchmarks History`, and `Settings`.
  - Retained `JarvisOrb` AGSL `RuntimeShader` (API 33+) and Canvas fallback (API 24–32) with lifecycle, thermal, battery-saver, and animator-scale throttling.
- **Security Hardening & Verification:**
  - Added `SecurityLogRedactor`, `HttpSecuritySupport.hfRedirectAuthSanitizer` (strips `Authorization` headers on external CDN redirects), and `SecureCredentialStore.verifyKeystoreHealth()`.
  - Expanded test coverage to **55 JVM/Robolectric tests** and **4 Android Instrumentation tests** (`JarvisInstrumentationTest` + `ExampleInstrumentedTest`).
- **Build & GitHub Actions CI Fix:**
  - Updated `app/proguard-rules.pro` with Shizuku AIDL/UserService and Kadb R8 keep rules.
  - Replaced prebuilt-APK-only workflows with a single unified source-build workflow (`.github/workflows/android-release.yml`) using JDK 21, Android SDK, Gradle 9.3.1, and automated unit testing.
