# Contributing to J.A.R.V.I.S Android

Thank you for your interest in contributing to **J.A.R.V.I.S Android** — an open-source, privacy-first autonomous Android AI Agent built with Kotlin, Jetpack Compose (Material 3), LiteRT-LM, Shizuku, Wireless ADB, and Accessibility/Vision automation.

---

## Ground Rules

1. **Preserve Privacy & Security Guarantees:**
   - Never hardcode API keys, tokens, or secrets in Kotlin, Gradle, or XML files.
   - Never disable `AppBlocklistPolicy`, `PublicHttpSafetyGuard` (SSRF protection), `PromptSecuritySanitizer`, or `SecurityLogRedactor`.
   - Never bypass confirmation gates for high-risk or irreversible actions (calls, messages, force-stop, destructive MCP writes, credential prompts).
   - Never add broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`); use the Android Photo Picker (`PickVisualMedia`).
2. **Honesty Over Mocks:**
   - Never fabricate hardware telemetry, mock unsupported model runtimes as working, or claim invisible background UI control that violates Android 12–16 sandbox limits.
3. **Architecture & UI Consistency:**
   - Keep Compose UI aligned with Material Design 3 and `JarvisThemeTokens` / `DesignTokens.kt` (`JarvisSpacing`, `JarvisRadii`, `JarvisGlassCard`, `JarvisStatusIndicator`).
   - Do not hardcode hex colors directly inside Composables.
   - Maintain minimum `48dp` touch targets (`minimumInteractiveComponentSize()`), meaningful `contentDescription` strings, and `snake_case` `Modifier.testTag(...)` on primary interactive elements.
   - Keep files modular and cohesive (aim to keep individual source files focused and readable).

---

## Development Setup

### Prerequisites
- **JDK 17** (or compatible JDK 11+ toolchain; `compileOptions` targets Java 11)
- **Android SDK** (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`)
- **Gradle 9.x** / Android Studio Ladybug+

### Local Build & Test Commands
```bash
# Run all JVM & Robolectric unit tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug

# Export fast ready-to-install APK (JARVIS-v1.0.2.apk)
./gradlew :app:fastApk

# Build R8-optimized release APK (requires keystore env vars)
./gradlew :app:assembleRelease
```

---

## Pull Request Workflow

1. Fork the repository and create a focused branch (`feat/your-feature`, `fix/your-bug`, `docs/your-update`).
2. Inspect related subsystems before changing code so you preserve existing workflows and Room migrations.
3. Add or update JVM / Robolectric unit tests under `app/src/test/java/com/example/` for all non-trivial logic changes.
4. Run the full unit test suite and ensure a clean build with zero warnings or regressions.
5. Open a Pull Request using the PR template and link any related issues.

---

## License

By contributing to this repository, you agree that your contributions will be licensed under the **GNU General Public License v3.0 (`GPL-3.0`)**. See [LICENSE](LICENSE) for details.
