# Build & Installation Guide — J.A.R.V.I.S Android (v1.0.0)

## Prerequisites
- **JDK:** 21 (Temurin recommended; bytecode target is Java 11)
- **Android SDK:** Compile SDK 36 (`minSdk 24`, `targetSdk 36`)
- **Gradle:** `9.3.1` (system `gradle` CLI or Android Studio; this repository uses `gradle` directly without a bundled `./gradlew` wrapper script)
- **Hardware:** `arm64-v8a` Android device recommended for local `.litertlm` inference (profiled target: Xiaomi Poco X7 Pro, MediaTek Dimensity 8400-Ultra, 12 GB RAM, Android 15/16, HyperOS)

---

## 1. One-Click Fast APK Build (Recommended)
To build a signed, ready-to-install APK (`JARVIS-v1.0.0.apk`) in the project root with zero manual setup:

```bash
# Linux / macOS / WSL (auto-creates local signing keystore if missing)
bash build-apk.sh

# Windows (Command Prompt / PowerShell)
build-apk.bat

# Fastest incremental Debug APK (copies directly to ./JARVIS-v1.0.0.apk)
gradle :app:fastApk --parallel --build-cache -x lint -x test
```

- **Default Output:** `./JARVIS-v1.0.0.apk` (signed and ready to install on any Android 7.0+ device).
- **Modes supported by `build-apk.sh` / `build-apk.bat`:**
  - *(no arguments)*: Fast signed Release APK (`-PfastApk=true`, skips slow R8 pass for rapid output).
  - `--debug`: Instant incremental signed Debug APK via `:app:fastApk`.
  - `--optimize`: Full R8-minified and resource-shrunk production Release APK.

---

## 2. Standard Gradle Build & Automated Test Suite
```bash
# Run full JVM + Robolectric unit/UI suite (44 tests)
gradle :app:testDebugUnitTest --no-daemon

# Run Android Lint checks
gradle :app:lintDebug --no-daemon

# Build installable Debug APK
gradle :app:assembleDebug --no-daemon
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Full Production R8 Release APK & App Bundle (Manual)
When `-PfastApk=true` is omitted, `release` builds run full R8 code shrinking (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`):
```bash
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"

gradle :app:assembleRelease :app:bundleRelease --no-daemon
```

---

## 3. Fast GitHub Actions APK Pipeline
The unified single-job workflow at [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) builds a signed `JARVIS-v1.0.0.apk` automatically on every push to `main`/`master`, every `v*` tag, or manual **Run workflow** dispatch:
- **Zero-Config Signing:** Uses `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` secrets when configured, or automatically generates a valid signing key so APK builds never fail.
- **Fast Mode by Default:** Skips redundant multi-job setup, slow R8 minification, and `.aab` bundling by default so your APK is ready in minutes (with optional workflow dispatch toggles for full R8, unit tests/lint, and `.aab`).

---

## 4. Installing & Configuring on an Android Device (e.g., Xiaomi Poco X7 Pro / HyperOS)
1. Enable **Developer Options** → **USB Debugging** (and **Install via USB** if installing via ADB).
2. Install the APK:
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
3. **HyperOS / OEM Background Optimization (Recommended for Voice & Downloads):**
   - App Info → Battery Saver → Set to **No restrictions** so background Hugging Face downloads (`ModelDownloadService`) and continuous voice standby (`JarvisVoiceService`) are not terminated prematurely by aggressive OEM battery management.
4. **Acquiring Gemma 3 1B Instruct (Offline `.litertlm` Setup):**
   - Open **Home** → **Settings** (or **More** → **Settings**) → paste a Hugging Face **Read** token if downloading gated repositories such as `litert-community/Gemma3-1B-IT` (after accepting the model license on Hugging Face).
   - Open **Models** → **Discover** or **Installed** → Download `.litertlm` or tap **Import `.litertlm`** from local storage.
   - Once verified (`SHA-256` match), tap **Load** to initialize GPU (OpenCL) with automatic CPU (XNNPACK) fallback.

---

## 5. Optional Cloud AI, Web Search & Wake-Word Keys
Never hardcode API keys into source code. Configure them inside the app's **Settings** or **Voice** screens (encrypted at rest using Android Keystore `AES-256-GCM` before storage in DataStore):
- **Hugging Face Access Token:** Settings → Local inference / Hugging Face token (for gated model repositories).
- **Picovoice Porcupine AccessKey:** Voice → Wake-word evaluation card (enables native `"Jarvis"` keyword spotting via `porcupine-android:4.0.2`).
- **Cloud AI & Web Search API Keys:** Settings → Hybrid routing and cloud APIs / Web research sections.
