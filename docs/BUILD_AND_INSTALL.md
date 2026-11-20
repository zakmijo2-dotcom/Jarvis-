# Build & Installation Guide — J.A.R.V.I.S Android (v1.0.2)

## Prerequisites
- **JDK:** 21 (Temurin recommended; Java 11 bytecode target)
- **Android SDK:** Compile SDK `36` (`minSdk 24`, `targetSdk 36`)
- **Gradle:** `9.3.1` (system `gradle` CLI or Android Studio; `gradle/wrapper/gradle-wrapper.properties` targets `9.3.1` without a bundled `./gradlew` binary)
- **Hardware:** `arm64-v8a` Android device recommended for local `.litertlm` inference

---

## 1. One-Click Signed APK Build (Recommended)
To produce a signed, ready-to-install APK (`./JARVIS-v1.0.2.apk`) with automatic keystore handling:

```bash
# Linux / macOS / WSL — Fast signed Release APK (-PfastApk=true)
bash build-apk.sh

# Windows (Command Prompt / PowerShell)
build-apk.bat

# Full R8-minified & resource-shrunk Release APK
bash build-apk.sh --optimize

# Instant signed Debug APK via :app:fastApk
bash build-apk.sh --debug
```

- **Script Modes (`build-apk.sh` / `build-apk.bat`):**
  - *(default / `--fast-release`)*: Builds `:app:assembleRelease -PfastApk=true` cleanly using a local `my-upload-key.jks` (auto-created if absent) and copies the signed APK to `./JARVIS-v1.0.2.apk`.
  - `--optimize`: Runs `:app:assembleRelease` with full R8 code shrinking, obfuscation, resource shrinking, and `app/proguard-rules.pro` rules for Shizuku AIDL, Kadb, LiteRT-LM, ML Kit OCR, Porcupine, Room v9, and OkHttp.
  - `--debug`: Restores `debug.keystore` from `debug.keystore.base64` (or generates it) and runs `:app:fastApk`.

---

## 2. Standard Gradle Commands & Verification Suite
```bash
# Run full JVM + Robolectric test suite (72 tests across 8 suites)
gradle :app:testDebugUnitTest --no-daemon

# Compile Android Instrumentation test suite (4 tests in androidTest)
gradle :app:compileDebugAndroidTestKotlin --no-daemon

# Build standard Debug APK -> app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleDebug --no-daemon
```

### Manual R8 Release APK & App Bundle Build
When omitting `-PfastApk=true`, `release` signing requires `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`:
```bash
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"

gradle :app:assembleRelease :app:bundleRelease --no-daemon
```

> **Note:** Never commit `.jks` or `.keystore` files or add a `local.properties` file to source control.

---

## 3. GitHub Actions CI/CD Pipeline
The unified single-job workflow at [`../.github/workflows/android-release.yml`](../.github/workflows/android-release.yml) builds and verifies the APK directly from source:
- **Toolchain Setup:** Installs **JDK 21 (Temurin)**, **Android SDK** (auto-accepting licenses), and **Gradle 9.3.1** with build caching.
- **Automated Quality Gate:** Runs `gradle :app:testDebugUnitTest --no-daemon` (all **72 JVM/Robolectric tests**) prior to APK assembly.
- **Build Modes:** Builds `fast-release` by default on branch pushes/PRs, automatically runs full **R8 Optimization** (`r8-release`) on `v*` tags, and exposes `build_mode` (`fast-release`, `r8-release`, `debug`) plus `run_tests` and `publish_github_release` inputs on manual dispatch.
- **Signing & Publishing:** Uses `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` secrets when configured, or generates an ephemeral keystore automatically, then uploads the signed APK + `SHA256SUMS.txt` to **Workflow Artifacts** and **GitHub Releases**.

---

## 4. Installing & Setting Up Device Bridges, Vision & Local AI
1. Install `JARVIS-v1.0.2.apk` (`adb install -r JARVIS-v1.0.2.apk`).
2. **Optional Advanced Operator Mode:** Open **Settings → Appearance & UI Mode** and toggle **Advanced Operator Mode** to unlock Home telemetry, Quick Autonomous Task bar, Device Controls, Shizuku/Wireless ADB setup, MCP servers, Checkpoints, App Knowledge Base, Skills JSON Export/Import, and Blocked Packages.
3. **Configure Shizuku (Recommended Primary Bridge):**
   - Install and start **Shizuku** (`moe.shizuku.privileged.api`) via Wireless Debugging or ADB.
   - Open **Shizuku & Wireless ADB Setup** → tap **Request Permission** and **Bind UserService**.
4. **Optional Pure-Kotlin Wireless ADB Pairing (`Android 11+`):**
   - Enable **Settings → Developer options → Wireless debugging**, enter pairing code & ports (or tap **Auto-Discover Ports** via mDNS), pair, and verify live `id` probe connection.
5. **Optional Non-Root Phone-Use Vision & OCR:**
   - Grant **Accessibility Service** (API 30+ `takeScreenshot` + element/coordinate gestures) and optionally authorize **Screen Capture Fallback (`MediaProjection`)** if on Android 7–10 (`API < 30`). Cloud screenshot transmission also requires explicit opt-in under **Cloud & Privacy**.
6. **Local `.litertlm` Model Setup:**
   - Open **Models** → search Hugging Face or import a `.litertlm` file (e.g., `gemma3-1b-it-int4.litertlm`), wait for `SHA-256` verification, and tap **Load**.
