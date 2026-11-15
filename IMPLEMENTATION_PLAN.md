# JARVIS — Autonomous Evolution Implementation Plan

## 1. Executive Summary
This plan evolves **JARVIS (`zakmijo2-dotcom/Jarvis-`)** from a local-first conversational assistant into a **verified, closed-loop autonomous Android agent** while preserving every working subsystem (LiteRT-LM inference, Hugging Face Model Hub, Voice Engine, Picovoice Porcupine, 17 Android tools, Obsidian Slate M3 UI, and Keystore security).

---

## 2. Audit Findings Summary (Pre-Evolution Baseline)
- **Strong Foundation (Preserve):** Real `litertlm-android:0.10.2` inference with GPU/CPU fallback, `RuntimeEngineCoordinator`, HTTP 206 resumable Hugging Face downloads with SHA-256 verification, multilingual `SpeechRecognizer`/`TextToSpeech` (`ar-DZ`, `ar-SA`, `fr-FR`, `en-US`), Picovoice `Porcupine 4.0.2`, Keystore `AES/GCM/NoPadding` encryption, and Room v6 WAL migrations (`1->6`).
- **Identified Evolution Gaps:**
  1. **Open-Loop Agent Execution:** Model-emitted `[TOOL_CALL]` directives were only dispatched once post-completion without feeding results back into the LLM, verifying post-action state, or supporting multi-step Observe→Think→Act→Verify→Recover iteration.
  2. **Flat Accessibility Layer:** Low-level `AccessibilityBridge` lacked a structured `AccessibilityController → UiObservationEngine → ElementIdentification → ActionExecutor → StateVerification` abstraction and pre/post state diff verification.
  3. **No Procedural Skills Engine or Memory Lifecycle:** `LocalMemory` lacked project context kind, duplicate suppression, access reinforcement, expiration pruning, and Hermes-style reusable procedural skill storage/execution.
  4. **Prompt-Injection Boundary:** External text from UI accessibility trees, notifications, and web search lacked centralized `[TOOL_CALL]` / role-delimiter sanitization.
  5. **Capability-Aware Multi-Model Routing:** `IntentRouter` lacked explicit per-model capability profiles (`toolCalling`, `vision`, `streaming`, `contextTokens`, `offlineCapable`) paired with hardware RAM/thermal constraints.
  6. **UI Visibility:** Missing expandable live Agent Execution Monitor, Skills Library manager UI, and dedicated Permissions & Privacy Center.

---

## 3. Staged Roadmap & Priorities

| Stage | Scope | Risk | Dependencies |
| :--- | :--- | :--- | :--- |
| **Stage 1–3** | Deep Audit, OSS/Competitor Harvest (`AbuZar-Ansarii/Jarvis`), Docs (`IMPLEMENTATION_PLAN.md`, `OPEN_SOURCE_CREDITS.md`, `TEST_PLAN.md`) | Low | None |
| **Stage 4** | Room v7 (`MIGRATION_6_7`) adding `agent_skills` table + memory importance/expiration columns; `PromptSecuritySanitizer`; `ModelCapabilityProfile` | Low-Med | Room KSP |
| **Stage 5** | Closed-loop `AgentCore` (Observe→Think→Act→Verify→Recover, bounded 3-iteration tool loop, duplicate guard, 1x recovery) | Medium | Stage 4, Stage 6 |
| **Stage 6** | `AccessibilityController` stack + State Verification; `RoomSkillEngine` + Memory lifecycle + 5 new tools (`retrieve_memory`, `save_memory`, `save_skill`, `run_skill`, `create_reminder`) | Medium | Stage 4 |
| **Stage 7** | Capability-aware `IntentRouter` & `RuntimeEngineCoordinator` capability queries | Low | Stage 4 |
| **Stage 8** | UI Evolution: Live Agent Execution Monitor (`ChatScreen`), Permissions & Privacy Tab (`DeviceDashboardScreen`), Skills Library (`SettingsScreen`) | Low | Stages 5–6 |
| **Stage 9–10** | Gradle release/debug builds, Robolectric + migration + agent security tests, `ARCHITECTURE.md` & `CHANGELOG.md` update | Low | All |
