# JARVIS — Evolution Changelog

## [2.0.0] — Autonomous Agent & Architecture Evolution
### Added
- **Closed-Loop Autonomous Agent Core (`Phase2AgentCore`):** Added Observe → Think → Act → Verify → Recover loop with pre-execution environment/screen observation, bounded multi-step `[TOOL_CALL]` feedback (max 3 iterations + duplicate-call guard), post-action state verification, and automatic workflow skill distillation.
- **Persistent Procedural Skills Engine (`RoomSkillEngine`, Room v7 `agent_skills`):** Reusable workflows with parameters, permission declarations, provenance, version tracking, validation, and strict exclusion of dangerous actions from automatic saving.
- **Enhanced Memory Lifecycle (`RoomLocalMemory`):** Added `MemoryKind.PROJECT`, normalized duplicate detection, access count & importance weighting, and automatic expiration pruning (`expiresAt`).
- **5-Layer Accessibility Controller Stack (`AccessibilityController.kt`):** Clean separation into `UiObservationEngine`, `ElementIdentificationEngine`, `ActionExecutor`, and `StateVerificationEngine` comparing before/after UI snapshots.
- **Prompt Injection Sanitizer (`PromptSecuritySanitizer.kt`):** Strips spoofed `[TOOL_CALL]` and role tags from untrusted UI/notification/web payloads and wraps them in `[UNTRUSTED_EXTERNAL_DATA]` boundaries.
- **5 New Registered Android Tools (Total: 22):** `retrieve_memory`, `save_memory`, `save_skill`, `run_skill`, and `create_reminder` (via standard Android `AlarmClock` intents).
- **Capability-Aware Task Routing (`ModelCapabilityProfile`):** Explicit capability modeling for local LiteRT-LM, Cloud models, and stubbed formats paired with RAM/thermal/network checks.
- **UI Additions:** Expandable Live Agent Execution Monitor (`ChatScreen`), dedicated Permissions & Privacy tab (`DeviceDashboardScreen`), and Reusable Skills Library (`SettingsScreen`).
- **Engineering Docs:** `IMPLEMENTATION_PLAN.md`, `OPEN_SOURCE_CREDITS.md`, `TEST_PLAN.md`, `CHANGELOG.md`, and updated `ARCHITECTURE.md`.

### Changed
- **Room Database:** Upgraded `JarvisDatabase` from v6 to v7 via non-destructive `MIGRATION_6_7`.

## [1.1.0] — Audit, Refactor & Obsidian Slate UI Overhaul
### Added
- Multi-runtime engine abstraction (`MultiRuntimeEngine.kt`), `Porcupine 4.0.2` `"Jarvis"` wake-word integration, and 4 on-demand Accessibility UI tools (`inspect_screen_elements`, `tap_ui_element`, `scroll_screen`, `input_ui_text`).
- Obsidian Slate & Titanium Material 3 palette, Unified Assistant Home header, message/code copy controls, and response regeneration.
