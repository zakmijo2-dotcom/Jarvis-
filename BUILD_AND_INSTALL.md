# Build & Installation Guide — J.A.R.V.I.S Android (v1.0.0)

## Prerequisites
- **JDK:** 21 (Temurin recommended; bytecode target is Java 11)
- **Android SDK:** Compile SDK 36 (`minSdk 24`, `targetSdk 36`)
- **Gradle:** `9.3.1` (system `gradle` CLI or Android Studio; this repository uses `gradle` directly without a bundled `./gradlew` wrapper script)
- **Hardware:** `arm64-v8a` Android device recommended for local `.litertlm` inference (profiled target: Xiaomi Poco X7 Pro, MediaTek Dimensity 8400-Ultra, 12 GB RAM, Android 15/16, HyperOS)

---

## 1. Local Debug Build & Automated Test Suite
```bash
# Run full JVM + Robolectric unit/UI suite (44 tests)
gradle :app:testDebugUnitTest --no-daemon

# Run Android Lint checks
gradle :app:lintDebug --no-daemon

# Build installable Debug APK
gradle :app:assembleDebug --no-daemon
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## 2. Production Release APK / App Bundle (R8 Minified)
Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true` using `app/proguard-rules.pro`.

### Step A: Generate an Upload Keystore (First Time Only)
```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### Step B: Build Signed Release APK & AAB
```bash
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"

gradle :app:assembleRelease :app:bundleRelease --no-daemon
```
Outputs:
- Signed APK: `app/build/outputs/apk/release/app-release.apk`
- Signed App Bundle: `app/build/outputs/bundle/release/app-release.aab`
- R8 Mapping File: `app/build/outputs/mapping/release/mapping.txt`

---

## 3. GitHub Actions CI/CD Secrets
The workflow at [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) automatically runs `:app:testDebugUnitTest` and `:app:lintDebug` on every PR/push, and builds signed Release artifacts when these repository secrets are configured:
- `ANDROID_KEYSTORE_BASE64` (`base64 -w0 my-upload-key.jks`, alias must be `upload`)
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

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
