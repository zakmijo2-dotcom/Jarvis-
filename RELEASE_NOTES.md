# J.A.R.V.I.S Android — Release Notes (v1.0.0)

- **Version:** `1.0.0` (`versionCode = 1`)
- **Application ID:** `com.aistudio.jarvis.k8v4mz`
- **License:** GNU General Public License v3.0 (`GPL-3.0`)
- **Platform Support:** Android 7.0 – Android 16 (`minSdk 24`, `compileSdk 36`, `targetSdk 36`)

---

## Highlights in v1.0.0

### 1. Shizuku-First Architecture & Pure-Kotlin Wireless ADB
- **Official Shizuku IPC (`13.1.5`):** Connects to Shizuku's privileged host process via `IJarvisShizukuService.aidl` (`JarvisShizukuUserService`), monitoring binder lifecycle, permission denial, and UID level (`root` vs `shell`) while strictly enforcing an allowlisted `argv` command model.
- **Wireless ADB Setup (`kadb:1.3.0`):** Dedicated onboarding screen for Android 11+ SPAKE2 + TLS 1.3 pairing, mDNS port auto-discovery (`NsdManager`), verified connection probing (`id`), and automatic masking/wiping of 6-digit pairing codes.
- **Unified Capability Routing (`DeviceCommandRouter`):** Automatically routes actions across **Shizuku → Wireless ADB → Native Android APIs → Accessibility Fallback**, with confirmation gates and execution verification (`pidof`, exit codes, screen state hashes).

### 2. Local AI Engineering & Hardware-Aware Model Hub
- **Verified LiteRT-LM Runtime (`0.10.2`):** Streams tokens from `.litertlm` bundles on GPU OpenCL / CPU XNNPACK with instant generation cancellation and `ComponentCallbacks2` memory pressure unloading.
- **Honest Format Guardrails:** Unbundled formats (`GGUF`, `.tflite`, `.task`, `.safetensors`) clearly show `Unsupported Format` with download/load disabled—zero fake compatibility badges.
- **Intelligent `ModelRouter` & `PromptCompressor`:** Evaluates RAM fit, thermal status, battery level, and recorded `model_benchmarks` to pick optimal models, cap context windows under load, block >85% RAM allocations, and compact older chat turns into concise factual digests.

### 3. Dark Graphite & Cyan-Blue UI Across 9 Redesigned Screens
- **Dual Theme Support:** Ships **Dark Graphite** (`#0E1015` / `#151922`) with restrained cyan-blue/teal/amber accents by default, plus **Light Alabaster** (`#F4F6F9` / `#FFFFFF`) and System Auto modes in **Settings → Appearance**.
- **9 Purpose-Built Screens:** `Home` telemetry dashboard, `Chat` (streaming, tool cards, confirmations, model switch, history drawer), `Models` hub, `More` hub, `Voice` console (`JarvisOrb` + multilingual STT/TTS + Porcupine wake-word), `Device Control`, `Shizuku & Wireless ADB Setup`, `Permissions & Security`, `Activity & Benchmarks History`, and `Settings`.
- **Typography & Localization:** Bundled **Space Grotesk**, **Plus Jakarta Sans**, and **JetBrains Mono** fonts; localized UI in English, Arabic (RTL), and French.

### 4. Security, Reliability & Build Fixes
- **Zero-Trust Safeguards:** Android Keystore `AES/GCM/NoPadding` (`SecureCredentialStore` with live health verification), `SecurityLogRedactor`, redirect `Authorization` header stripping (`HttpSecuritySupport`), and `PromptSecuritySanitizer`.
- **CI & R8 Release Packaging:** Full ProGuard/R8 keep rules for Shizuku AIDL and Kadb, plus a genuine build-from-source GitHub Actions workflow producing verified signed APKs and `SHA256SUMS.txt`.
