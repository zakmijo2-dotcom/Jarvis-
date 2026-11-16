# Security, Privacy & Play Policy Audit (`JARVIS v1.0.1`)

---

## 1. Threat Model & Security Controls

| Risk Surface | Concrete Threat | Implemented Control |
| :--- | :--- | :--- |
| **Credential Storage** | Plaintext API keys (`Cloud`, `HuggingFace`, `Brave`, `SerpAPI`, `Tavily`, `Custom Web`, `MCP Bearer`) in DataStore preferences or logs. | Stored exclusively inside `SecureCredentialStore` encrypted with **Android Keystore AES-256-GCM** (`NoPadding`) and scrubbed by `SecurityLogRedactor` before logging or UI display. |
| **Sensitive App & Password Exposure** | Autonomous screenshots, OCR, or accessibility hierarchy dumps reading bank balances, OTPs, password managers, or Android `inputPassword` fields. | 1) `ScreenTreeCompressor` replaces password/PIN/OTP node text with `[REDACTED_PASSWORD]`.<br>2) `AppBlocklistPolicy` ships a default blocklist covering major banking, payment, authenticator, and package-installer packages plus user custom blocklist entries, blocking both automated UI actions and visual/OCR capture.<br>3) `ScreenRiskClassifier` escalates transfer/payment/delete/send/permission-grant UI states to mandatory confirmation. |
| **Cloud Visual Privacy** | Unexpected upload of device screenshots to external cloud LLM APIs. | Visual attachments are sent **only** when `cloudUploadConsentGranted == true` AND (`screenshotVisionEnabled == true` OR user explicitly attached an image via `PickVisualMedia`), and blocked packages are rejected prior to encoding. |
| **SSRF via `web_search` / `fetch_webpage` / `open_url`** | Malicious prompt or webpage redirects inducing `OkHttp` to probe `127.0.0.1`, RFC-1918 LAN (`10.0.0.0/8`, `192.168.0.0/16`), link-local / cloud metadata (`169.254.169.254`), or internal IPv6 ranges. | `PublicHttpSafetyGuard` enforces `http`/`https` schemes, validates literal & DNS-resolved `InetAddress` entries (rejecting loopback, site-local, link-local, multicast, unspecified, CGNAT `100.64.0.0/10`, and IPv6 ULA `fc00::/7`), checks every redirect hop, and strips `Authorization` headers across redirect domains. |
| **Prompt Injection via Screen Text & Web Pages** | Hostile web content or on-screen notifications containing spoofed `[TOOL_CALL]`, `<|system|>`, or `"ignore all previous instructions"` payloads. | All Accessibility trees, OCR text, fetched webpages, and MCP tool responses are passed through `PromptSecuritySanitizer.wrapUntrustedBlock(...)` before entering model context. |
| **Accessibility Overlay Touch Jacking / Stuck Views** | Floating status HUD intercepting user taps or corrupting screenshots during automation. | `ExecutionOverlayManager` attaches only non-blocking `TYPE_ACCESSIBILITY_OVERLAY` windows (`FLAG_NOT_FOCUSABLE`), hides overlay visibility immediately around bitmap capture (`hiddenForCapture`), guarantees teardown inside `finally`/cancellation hooks, and degrades cleanly to in-app UI HUD when unavailable. |
| **MCP Remote Tool Execution** | Untrusted or write-capable MCP server tools executing destructive actions silently. | MCP servers are disabled by default unless explicitly added/enabled by user in Room `mcp_servers`, require HTTPS (except explicit user-configured localhost/LAN dev endpoints guarded by validation), and gate mutating/write tool invocations behind user confirmation (`ToolRiskLevel.MEDIUM`/`HIGH`). |
| **Google Play Policy Compliance** | Broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`) or non-Play dynamic code loading (`.dex`, `.jar`, QuickJS arbitrary script execution). | Zero broad storage permissions declared; user image attachments use zero-permission `ActivityResultContracts.PickVisualMedia`; QuickJS/DCL avoided completely; `FOREGROUND_SERVICE_MEDIA_PROJECTION` uses Android 14+ foreground service type and user media projection consent dialog. |

---

## 2. Default Safe-State Invariants Preserved

1. `wakeWordStandbyEnabled = false` on clean install (no background mic capture unless opted in).
2. Local AI stays unloaded / disabled unless the user downloads and explicitly loads a valid `.litertlm` model.
3. `cloudUploadConsentGranted = false` (no silent prompt or screenshot uploads).
4. `webResearchEnabled = false` until explicitly toggled on by the user.
5. `accessibilityAutomationEnabled = false` and `screenshotVisionEnabled = false` until user explicitly enables accessibility service / visual mode.
6. Shizuku `UserService` (`JarvisShizukuUserService`) enforces strict `argv` allowlisting without assuming UID 0 root.
