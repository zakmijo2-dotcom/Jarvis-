# Open-Source Attributions & Clean-Room Provenance

> **JARVIS License:** GNU General Public License v3.0 (`GPL-3.0`)  
> **Scope:** Documents all open-source repositories studied during the autonomous phone-use upgrade, their exact copyright notices and licenses, architectural concepts adapted via clean-room Kotlin implementation, and explicit exclusions.

---

## 1. Clean-Room Implementation Policy

All new modules added to JARVIS (`core/vision/`, `core/mcp/`, `core/overlay/`, upgraded `core/web/`, `core/agent/`, `core/tools/`, `core/security/`, and `ui/`) were authored in **clean-room Kotlin** inside JARVIS's existing architecture rather than copying external code verbatim. All compatible reference repositories carry **MIT** or **Apache-2.0** licenses, both of which are compatible with JARVIS's **GPL-3.0** distribution.

---

## 2. Permissively Licensed Reference Projects

### A. OpenPhoneUse
- **Repository:** `https://github.com/joungminsung/openphoneuse.git`
- **Commit:** `c980cbf134a5d5eb1ec6cd097fcfabc45d6a73` (`main`, `2026-11-15 11:51:15 +0900`)
- **License:** MIT License — `Copyright (c) 2026 joungminsung`
- **Architectural Concepts Adapted:**
  - Indexed, centered-coordinate UI hierarchy compression (`ScreenTreeCompressor.kt`)
  - Screen visit & action loop tracking (`ScreenJournal.kt`)
  - Pre-execution screen risk gate (`ScreenRiskClassifier.kt`)
  - Accessibility-attached HUD overlay with capture-hiding & touch-pass-through (`ExecutionOverlayManager.kt`)

### B. Juyu Phone Agent
- **Repository:** `https://github.com/goehou/Juyu-phone-agent.git`
- **Commit:** `7810db36e2b030d0bd55c91297b106314a4caad7` (`main`, `2026-08-01 16:25:12 +0800`)
- **License:** Apache License 2.0
- **Architectural Concepts Adapted:**
  - API 30+ `AccessibilityService.takeScreenshot()` visual capture flow and JPEG Base64 downscaling (`ScreenPerceptionEngine.kt`, `CloudImageAttachment`)
  - Closed-loop multimodal step tracing and instant stop cancellation (`Phase2AgentCore.kt`)
  - Lightweight HTTP JSON-RPC Model Context Protocol (`MCP`) client patterns (`McpClient.kt`)

### C. OpenRing
- **Repository:** `https://github.com/SunZhi-Will/OpenRing.git`
- **Commit:** `b61094ab9e427d14913e61cea194fe9f4cee43cb` (`main`, `2026-04-12 17:25:54 +0800`)
- **License:** MIT License — `Copyright (c) 2026 OpenRing Contributors`
- **Architectural Concepts Adapted:**
  - Android 14+ `MediaProjection` foreground service & callback capture fallback (`MediaProjectionForegroundService.kt`, `MediaProjectionCaptureController.kt`)
  - Multi-strategy click/text action fallback (native `ACTION_CLICK` → clickable ancestor → `dispatchGesture` coordinates)
- **Not Integrated:** `SkillQuickJsExecutor` (QuickJS dynamic script execution avoided for Play DCL compliance and absence of NDK toolchain).

### D. Zafiro
- **Repository:** `https://github.com/niki914/zafiro.git`
- **Commit:** `275ce1bd41949cc4ac3fc71715aef119b0469ac1` (`main`, `2026-11-16 23:40:51 +0900`)
- **License:** MIT License — `Copyright (c) 2026 niki914`
- **Architectural Concepts Adapted:**
  - Recursive DFS node extraction with `AccessibilityNodeInfo` recycling and off-screen scrollable culling (`JarvisAccessibilityService.kt`)
  - Bounded observation pruning across multi-turn agent loops (`Phase2AgentCore.kt`)
  - Structured MCP server registry separation (`McpManager.kt`)

### E. AndroidHarness
- **Repository:** `https://github.com/Sanuu7/androidharness.git`
- **Commit:** `28ceacb04ee4ea4117cc46d417e49be3304dc085` (`main`, `2026-11-13 00:09:14 +0530`)
- **License:** MIT License — `Copyright (c) 2026 Sanuu7`
- **Architectural Concepts Adapted:**
  - Strict public-IP DNS/CIDR SSRF validation (`PublicHttpSafetyGuard.kt`)
  - Modular Brave/Tavily/Keyless web search providers & clean HTML/JSON-LD readable page extractor (`OkHttpWebResearchService.kt`, `WebPageExtractor.kt`)
  - Persistent execution checkpoints (`AgentCheckpointEntity`, `AgentCheckpointDao`)

---

## 3. Excluded Reference Project (Non-Compatible License & OS Patches)

### OpenPhone (`https://github.com/secondly-com/openphone.git`)
- **Commit:** `b4b6e76fc353bac249a9877cdda9722e7b3f3db8` (`main`, `2026-08-18 15:01:43 -0700`)
- **License:** OpenPhone Mixed License (includes **PolyForm Noncommercial 1.0.0** and OS-level patches; `Copyright 2026 Dafdef, inc.`).
- **Compliance Statement:** **NO source files, patches, APKs, binaries, or build artifacts** from `openphone` were copied, derived, linked, or packaged into JARVIS. It was inspected solely for high-level conceptual architecture regarding OS tool auditability and risk classification.

---

## 4. Bundled Android & Maven Dependencies Added

- `com.google.mlkit:text-recognition:16.0.1` (Google Android / ML Kit Text Recognition SDK Terms, bundled Latin text model for offline on-device OCR without requiring cloud uploads).
