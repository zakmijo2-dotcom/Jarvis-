# Build & Installation Guide — JARVIS Android (v1.0.0)

## Prerequisites
- **JDK:** 17+
- **Android SDK:** Compile SDK 36 (`minSdk 24`, `targetSdk 36`)
- **Gradle:** System `gradle` or Android Studio Ladybug+ (Note: AI Studio container uses system `gradle` directly without `gradlew`).
- **Hardware:** `arm64-v8a` Android device recommended (tested profile: Xiaomi Poco X7 Pro, Dimensity 8400-Ultra, 12 GB RAM).

---

## 1. Local Debug Build & Automated Test Suite
```bash
# Run full JVM + Robolectric unit/UI suite
gradle :app:testDebugUnitTest

# Run Android Lint checks
gradle :app:lintDebug

# Build installable Debug APK
gradle :app:assembleDebug
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

gradle :app:assembleRelease :app:bundleRelease
```
Outputs:
- Signed APK: `app/build/outputs/apk/release/app-release.apk`
- Signed App Bundle: `app/build/outputs/bundle/release/app-release.aab`
- R8 Mapping File: `app/build/outputs/mapping/release/mapping.txt`

---

## 3. GitHub Actions CI/CD Secrets
The workflow at `.github/workflows/android-release.yml` automatically runs `:app:testDebugUnitTest` and `:app:lintDebug` on every PR/push, and builds signed Release artifacts when these repository secrets are configured:
- `ANDROID_KEYSTORE_BASE64` (`base64 -w0 my-upload-key.jks`)
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`

---

## 4. Installing on Xiaomi Poco X7 Pro (HyperOS)
1. Enable **Developer Options** -> **USB Debugging** (and **Install via USB** if installing via ADB).
2. Install the APK:
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
3. **HyperOS Background Optimization (Recommended for Voice / Downloads):**
   - App Info -> Battery Saver -> Set to **No restrictions** so background Hugging Face downloads (`ModelDownloadService`) are not killed by HyperOS power management.
4. **Acquiring Gemma 3 1B Instruct (Offline Setup):**
   - Open **Settings** -> paste a Hugging Face **Read** token if downloading gated `litert-community/Gemma3-1B-IT` (after accepting its license on Hugging Face).
   - Open **Models** -> **Discover** or **Installed** -> Download `.litertlm` or tap **Import `.litertlm`** from local storage.
   - Once verified (`SHA-256` match), tap **Load Engine** to initialize GPU (OpenCL) with automatic CPU fallback.

## 5. Optional Cloud AI, Web Search & Wake-Word Keys
Never hardcode API keys into source code. Configure them securely inside the app's **Settings** or **Voice Console** screens (encrypted at rest using Android Keystore AES-256-GCM before storage in DataStore):
- **Hugging Face Access Token:** Settings -> Hugging Face Token (for gated model repositories).
- **Picovoice Porcupine AccessKey:** Voice Console -> Wake Word Card (enables native `"Jarvis"` keyword spotting via `porcupine-android:4.0.2`).
- **Cloud AI & Web Search API Keys:** Settings -> Hybrid Routing / Web Research cards.

