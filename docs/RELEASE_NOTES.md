# J.A.R.V.I.S Android — Release Notes (v1.0.2)

- **Version:** `1.0.2` (`versionCode = 3`)
- **Application ID:** `com.aistudio.jarvis.k8v4mz`
- **License:** GNU General Public License v3.0 (`GPL-3.0`)
- **Platform Support:** Android 7.0 – Android 16 (`minSdk 24`, `compileSdk 36`, `targetSdk 36`)
- **Verified Automated Tests:** **72 / 72** JVM & Robolectric tests across **8 suites** (`0` failures, `0` errors, `0` skipped)

---

## Highlights in v1.0.2

### 1. Goal-Oriented Autonomous Agent Core & Live Execution Controls
- **Goal Decomposition & Dynamic Replanning (`GoalTaskPlanner`, `Phase2AgentCore`):** Breaks composite user requests into verifiable multi-step plans, preserves explicit URL routing over incidental app-name collisions, tracks explicit `AgentExecutionPhase` states, records structured `AgentTimelineEntry` logs, and triggers bounded dynamic replanning (up to 3 replans per run) on unexpected screen transitions or failures.
- **Transparent Background Execution (`AgentBackgroundCoordinator`, `AgentExecutionForegroundService`):** Runs foreground tasks with persistent **Pause / Resume / Stop** notification controls, keyguard lock-state safety checks, interrupted checkpoint detection on restart, and honest Android 12–16 `VirtualDisplayFeasibilityProbe` reporting.
- **Unified Automation & Visual Localization (`PhoneAutomationEngine`, `ScreenClassifier`, `VisualElementLocator`):** Adds `wait_for_screen`, `locate_and_tap_visual`, and `navigate_device` plus 9-class screen state classification and context-aware cloud screenshot gating.

### 2. Stateful Multi-Tab Browser Automation & 43 Registered Tools
- **SSRF-Safe Browser Engine (`BrowserAutomationEngine`, `BrowserTools`):** Adds 6 stateful browser tools (`browser_open`, `browser_read`, `browser_click_link`, `browser_fill_form`, `browser_back`, `browser_tabs`) with JSON-LD extraction and `PublicHttpSafetyGuard` protection across initial requests and HTTP redirects, bringing `ToolRegistry` to **43 registered tools**.
- **Capability-Aware Cloud Routing (`ModelCapabilityDetector`):** Matches task complexity and multimodal/tool requirements to fast, balanced, reasoning, or vision model tiers.

### 3. Application Knowledge Base, Skills JSON Exchange & Room Database v9
- **Room Database v9 (`MIGRATION_8_9`, 12 Tables):** Adds the `app_knowledge` table and `AppKnowledgeRepository` to learn verified per-package UI labels and navigation hints while automatically filtering sensitive secrets and blocked packages.
- **Skills Library Upgrades:** Adds real-time search filtering plus JSON Export (with one-tap clipboard copy) and JSON Import directly in **Settings**.

### 4. Refined Visual Identity, Brand Assets & Open-Source Polish
- **Design System & Autonomous UX:** Introduces `DesignTokens.kt`, `JarvisDesignSystem.kt` (`JarvisGlassCard`, `JarvisStatusIndicator`, memoized `JarvisMarkdownText`), and `AgentActivityComponents.kt` (`AgentPhaseBadge`, collapsible live tool timeline, `ScreenObservationPreviewCard`, `AgentStopSummaryBanner`) wired across `HomeScreen` (with inline Quick Autonomous Task bar) and `ChatScreen`.
- **Original Brand Assets & Repository Masterpiece:** Ships a custom J.A.R.V.I.S adaptive launcher icon + 5-density mipmap set, `docs/assets/jarvis-logo.png`, `docs/assets/jarvis-banner.png`, reorganized `/docs/`, and full community governance files (`CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, GitHub Issue & PR templates).

### 5. Preserved Core Guarantees
- Official **Shizuku (`13.1.5`)** `UserService` + **Kadb (`1.3.0`)** pure-Kotlin Wireless ADB SPAKE2/TLS bridge.
- Honest **LiteRT-LM (`0.10.2`)** `.litertlm` runtime (`GGUF`/`.tflite`/`.task`/`.safetensors` explicitly unsupported).
- Android Keystore **AES-256-GCM** credential encryption, zero broad storage permissions (`PickVisualMedia`), and **Dark Graphite** / **Light Alabaster** dual-theme support.
