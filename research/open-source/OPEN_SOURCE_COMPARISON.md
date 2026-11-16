# Open-Source Autonomous Android Agent Research & Comparison

> **Target Application:** `J.A.R.V.I.S` (`https://github.com/zakmijo2-dotcom/Jarvis-.git`, branch `main`, commit `ee858a3965fe10cb076bfd9532a8d02fa9847c82`, `2026-11-16 22:44:57 +0100`, License: `GPL-3.0`)  
> **Research Workspace:** `research/open-source/repos/`  
> **Audit Method:** Direct inspection of source files, `AndroidManifest.xml`, Gradle/build manifests, scripting/native layers, and `LICENSE` headers—never trusting `README.md` claims without code verification.

---

## 1. Repository Inventory

| ID | Project | Repository URL | Branch | Commit Hash | Latest Commit Date | License | APK Compatibility |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Target** | JARVIS Upstream | `https://github.com/zakmijo2-dotcom/Jarvis-.git` | `main` | `ee858a3965fe10cb076bfd9532a8d02fa9847c82` | `2026-11-16 22:44:57 +0100` | GNU GPL-3.0 | Baseline App |
| **A** | OpenPhoneUse | `https://github.com/joungminsung/openphoneuse.git` | `main` | `c980cbf134a5d5eb1ec6cd097fcfabc45d6a73` | `2026-11-15 11:51:15 +0900` | MIT | Fully Compatible (Clean-Room Kotlin) |
| **B** | Juyu Phone Agent | `https://github.com/goehou/Juyu-phone-agent.git` | `main` | `7810db36e2b030d0bd55c91297b106314a4caad7` | `2026-08-01 16:25:12 +0800` | Apache-2.0 | Fully Compatible (Clean-Room Kotlin) |
| **C** | OpenRing | `https://github.com/SunZhi-Will/OpenRing.git` | `main` | `b61094ab9e427d14913e61cea194fe9f4cee43cb` | `2026-04-12 17:25:54 +0800` | MIT | Partial Concept Compatibility (`MediaProjection` & multi-strategy input); QuickJS script engine skipped due to Play DCL/no-NDK constraints |
| **D** | Zafiro | `https://github.com/niki914/zafiro.git` | `main` | `275ce1bd41949cc4ac3fc71715aef119b0469ac1` | `2026-11-16 23:40:51 +0900` | MIT | Fully Compatible (Clean-Room Kotlin + Shizuku + MCP) |
| **E** | AndroidHarness | `https://github.com/Sanuu7/androidharness.git` | `main` | `28ceacb04ee4ea4117cc46d417e49be3304dc085` | `2026-11-13 00:09:14 +0530` | MIT | Fully Compatible (Clean-Room Kotlin/JVM HTTP, SSRF, Checkpoints) |
| **Ref** | OpenPhone | `https://github.com/secondly-com/openphone.git` | `main` | `b4b6e76fc353bac249a9877cdda9722e7b3f3db8` | `2026-08-18 15:01:43 -0700` | OpenPhone Mixed License (PolyForm Noncommercial 1.0.0 + AOSP patches) | **INCOMPATIBLE FOR CODE REUSE** — OS framework patchset & noncommercial license. Policy concepts only. |

---

## 2. Per-Project Architecture & Source-Verified Audit

### Project A — OpenPhoneUse (`openphoneuse`)
- **Language / Build Stack:** Kotlin, Jetpack Compose, Gradle Android application, AccessibilityService + Overlay WindowManager.
- **Verified Implementation Highlights:**
  - `AgentLoop.kt`: Multi-step **Observe → Plan → Act → Verify** execution loop driven by fresh post-action observations, tool responses, and completion checks.
  - `TreeCompressor.kt` & `UiNode.kt`: Converts live Accessibility `AccessibilityNodeInfo` hierarchies into compact indexed UI elements containing center `(cx, cy)` bounds, clickable/editable flags, and compressed text representations suitable for constrained LLM context windows.
  - `ScreenJournal.kt`: Tracks visited screen hashes/signatures and action traces across loop turns to detect stalls, repeated clicks, or circular navigation.
  - `RiskGuard.kt`: Classifies sensitive operations before execution (financial actions, deletion, credential prompts, outbound communication) and triggers confirmation checkpoints.
  - `OverlayManager.kt`: Uses `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` (zero extra `SYSTEM_ALERT_WINDOW` permission when AccessibilityService is active) with `FLAG_NOT_TOUCHABLE` / `hiddenForCapture` toggles and frame-commit synchronization so overlays do not occlude automated screenshots or accidentally block user/agent touches.
- **Security & Risk Notes:** Unchecked coordinate taps on password/banking UIs can leak credentials if screenshots or trees are sent to cloud LLMs. Clean-room design in JARVIS adds `[REDACTED_PASSWORD]` masking + `AppBlocklistPolicy`.

### Project B — Juyu Phone Agent (`Juyu-phone-agent`)
- **Language / Build Stack:** Kotlin, Jetpack Compose, AccessibilityService, HTTP/OkHttp cloud multimodal providers + MCP client.
- **Verified Implementation Highlights:**
  - `AgentAccessibilityService.kt`: Uses Android 11+ (API 30+) `AccessibilityService.takeScreenshot(Display.DEFAULT_DISPLAY, ...)` directly from the AccessibilityService without requiring extra runtime user consent prompts on API 30+, plus gesture dispatch (`dispatchGesture`).
  - `ScreenshotEncoder.kt`: Downscales and JPEG-compresses captured bitmaps before Base64 multimodal upload to control payload size and latency.
  - `AgentLoop.kt`: Structured multi-turn action dispatch loop with explicit tool execution traces and cancellation checks between actions.
  - `McpClient.kt`: Lightweight JSON-RPC 2.0 MCP client over HTTP supporting tool discovery (`tools/list`) and tool invocation (`tools/call`).
  - `OverlayController.kt`: Live floating status pill with step telemetry and immediate abort/stop affordance.
- **Security & Risk Notes:** `takeScreenshot()` captures whatever is on screen; JARVIS must gate cloud screenshot uploads behind `cloudUploadConsentGranted`, `screenshotVisionEnabled`, and `AppBlocklistPolicy` so banking apps or password managers are never captured or transmitted.

### Project C — OpenRing (`OpenRing`)
- **Language / Build Stack:** Kotlin Android RPA engine, `MediaProjectionHostService`, Accessibility gesture execution, QuickJS scripting bindings (`.js` skill packages).
- **Verified Implementation Highlights:**
  - `ScreenCapture.kt` & `MediaProjectionHostService.kt`: Demonstrates proper foreground service lifecycle (`FOREGROUND_SERVICE_MEDIA_PROJECTION`) and `MediaProjection` frame capture fallback for devices < API 30 or contexts where `AccessibilityService.takeScreenshot()` fails.
  - `ActionExecutor.kt`: Multi-strategy node clicking (accessibility `ACTION_CLICK` → clickable ancestor traversal → coordinate `dispatchGesture` bounds fallback) and multi-strategy text input (`ACTION_SET_TEXT` + clipboard/paste fallback).
  - `SkillQuickJsExecutor.kt`: JavaScript-based QuickJS RPA runtime.
- **Incompatible / Skipped Components:** QuickJS dynamic JavaScript execution requires native bindings or risks violating Google Play's Dynamic Code Loading (DCL) rules for unverified executable scripts; no C/C++ NDK toolchain is bundled in this build environment. JARVIS preserves its typed, inspectable Kotlin `RoomSkillEngine` with parameterized workflows instead.

### Project D — Zafiro (`zafiro`)
- **Language / Build Stack:** Kotlin, Jetpack Compose, Shizuku IPC, AccessibilityService tree extractor, MCP service integration, modular tool & context pruning layers.
- **Verified Implementation Highlights:**
  - `A11yDisplayPort.kt` & `DisplayNodeExtractor.kt`: Performs depth-first search (`DFS`) node extraction with proper `AccessibilityNodeInfo.recycle()` hygiene, off-screen/scrollable child culling, and visibility/bounds validation.
  - `TreeFormatter.kt`: Builds dense, high-signal textual screen trees for LLM reasoning alongside indexed interactive targets.
  - `ContextPruner.kt`: Prunes older bloated tool/screen observations from multi-turn context while keeping recent observations crisp and accurate.
  - `McpServiceImpl.kt`: Clean separation between MCP transport, server configuration, and tool registry registration.
- **Overlap with Existing JARVIS:** JARVIS already ships a newer `ShizukuController` + `JarvisShizukuUserService` (`13.1.5`) + `WirelessAdbController` (`kadb 1.3.0`) + `DeviceCommandRouter`; Zafiro's tree formatting, `DFS` culling, node recycling, and context pruning plug cleanly into JARVIS without touching Shizuku/ADB transport.

### Project E — AndroidHarness (`AndroidHarness`)
- **Language / Build Stack:** Kotlin, OkHttp, Room/SQLite checkpoints, multi-backend search/fetch research pipeline, subagent & shell/file runtime abstractions.
- **Verified Implementation Highlights:**
  - `PublicHttpAddresses.kt`: Strict SSRF protection resolving DNS and blocking loopback (`127.0.0.0/8`, `::1`), RFC-1918 private subnets (`10/8`, `172.16/12`, `192.168/16`), link-local / cloud metadata (`169.254.169.254`), multicast, and IPv6 ULA/link-local addresses.
  - `SearchBackends.kt` (`BraveApiBackend`, `TavilyApiBackend`, `KeylessSearchBackend`, `WebResultCache`): Modular web search providers supporting API-backed search + keyless fallback.
  - `ResearchHttp.kt`, `StructuredPageText.kt`, `WebSearchText.kt`: Bounded-byte OkHttp HTML fetcher + clean HTML/JSON-LD readable content extractor + per-host request cooldowns.
  - Session checkpoint persistence pattern enabling recovery across interrupted multi-step tasks.
- **Security & Risk Notes:** Unrestricted shell execution in general harness tools violates Android APK sandboxing and Google Play safety; JARVIS keeps shell access strictly inside its allowlisted `JarvisShizukuUserService` and exposes only curated capabilities to the agent.

### Optional Reference — OpenPhone (`openphone`)
- **Language / Build Stack:** AOSP system framework patches (Java + Android platform services), privileged permission policy engine.
- **Verified Findings:** `PolicyEngine.java` models fine-grained system capability risk matrices and policy audits before elevated execution.
- **License & Integration Boundary:** OpenPhone uses a mixed model referencing **PolyForm Noncommercial 1.0.0** and OS-level AOSP framework modifications. Per license and platform boundaries, **zero source code, headers, or patches** from `openphone` are copied or linked into JARVIS.

---

## 3. Clean-Room Architectural Synthesis for JARVIS v1.0.1

| Capability Domain | Best-in-Class Reference Inspiration | JARVIS v1.0.1 Clean-Room Implementation |
| :--- | :--- | :--- |
| **Screen Perception & OCR** | OpenPhoneUse (`TreeCompressor`, `ScreenJournal`) + Juyu (`takeScreenshot`, `ScreenshotEncoder`) + OpenRing (`MediaProjection`) + Zafiro (`DisplayNodeExtractor`) | Unified `core/vision/` package (`ScreenPerceptionEngine`, `ScreenTreeCompressor`, `ScreenJournal`, `OnDeviceOcrEngine` via ML Kit `16.0.1`, `MediaProjectionCaptureController` + `MediaProjectionForegroundService`) |
| **Multimodal Cloud Payloads** | Juyu (`ScreenshotEncoder`) | `CloudImageAttachment` in `CloudAiClient` supporting Gemini `inlineData`, OpenAI/OpenRouter `image_url`, and Anthropic `image` |
| **Safety, Risk & Overlay HUD** | OpenPhoneUse (`RiskGuard`, `OverlayManager`) + OpenPhone (policy concepts) + Juyu (`OverlayController`) | `ScreenRiskClassifier`, `AppBlocklistPolicy`, `ExecutionOverlayManager` (`TYPE_ACCESSIBILITY_OVERLAY` with in-app HUD fallback) |
| **Closed-Loop Agent & Checkpoints** | OpenPhoneUse (`AgentLoop`) + Zafiro (`ContextPruner`) + AndroidHarness (checkpoints) | Upgraded `Phase2AgentCore` (up to 12 closed-loop turns, loop/stall detection, alternate recovery, steering, Room `agent_checkpoints` persistence & resume) |
| **Web Search & Page Fetch** | AndroidHarness (`SearchBackends`, `PublicHttpAddresses`, `StructuredPageText`) | `PublicHttpSafetyGuard`, `WebPageExtractor` (`fetch_webpage` tool), `OkHttpWebResearchService` (Brave Search, SerpAPI, Tavily, Serper, Wikipedia, Keyless fallback, Custom JSON, `research_history` DAO) |
| **MCP Client** | Juyu (`McpClient`) + Zafiro (`McpServiceImpl`) | `core/mcp/` (`McpClient` Streamable HTTP/SSE + `Mcp-Session-Id`, `McpManager` backed by Room `mcp_servers`) |
| **Claude-Inspired Dual-Mode UX** | Clean chat-first assistant UX + existing JARVIS Graphite/Cyan system | Default Mode (`uiAdvancedModeEnabled = false`: clean chat, image picker, model pill, history drawer, step HUD + Stop) vs. Advanced Operator Mode (`true`: full 4-tab telemetry, Shizuku/ADB, MCP, Checkpoints, Blocklist, Diagnostics) |
