# Security Policy

**J.A.R.V.I.S Android** can interact with device UI, accessibility trees, local memory, Shizuku / Wireless ADB bridges, MCP servers, and optional user-configured cloud AI providers. We treat security and user privacy as first-class engineering requirements.

---

## Supported Versions

| Version | Supported |
| ------- | --------- |
| `1.0.x` | Yes       |
| `< 1.0` | No        |

---

## Security Architecture Highlights

- **Encrypted Credential Storage:** Cloud API keys, Hugging Face tokens, Brave/SerpAPI keys, and MCP bearer tokens are stored using Android Keystore-backed AES-256-GCM encryption (`SecureCredentialStore`) and never written to plaintext logs or backups (`android:allowBackup="false"`).
- **Explicit Cloud Consent:** No prompt, screen observation, or screenshot leaves the device unless the user explicitly enables cloud transmission in Settings.
- **Sensitive App Blocklist (`AppBlocklistPolicy`):** Banking apps, password managers, authenticators, wallets, and Android package installers are automatically blocked from automated interaction and visual capture.
- **SSRF & Redirect Protection (`PublicHttpSafetyGuard`):** Web research, browser automation, and MCP connections block loopback, RFC-1918 private LANs, link-local / cloud metadata endpoints (`169.254.169.254`), CGNAT, and IPv6 ULA addresses across initial requests and HTTP redirects.
- **Prompt Injection Defense (`PromptSecuritySanitizer`):** Untrusted screen trees, OCR output, notifications, and fetched webpages are wrapped and stripped of forged tool-call directives.
- **Log Redaction (`SecurityLogRedactor`):** Bearer tokens, API keys, and 6-digit Wireless ADB pairing codes are scrubbed from logs and diagnostics.
- **Human-in-the-Loop Confirmation:** High-risk actions (phone calls, messages, force-stop, destructive MCP tool calls, payment/credential dialogs) require explicit user approval.

---

## Reporting a Vulnerability

**Please do not report security vulnerabilities via public GitHub issues.**

If you discover a security flaw (such as an SSRF bypass, blocklist bypass, credential leak, prompt-injection escalation, or unsafe ADB/Shizuku execution path):

1. Use **GitHub Private Vulnerability Reporting** under the repository's **Security -> Advisories** tab, or contact the repository maintainer directly.
2. Include:
   - Affected component / file path and version (`versionName` / commit hash)
   - Clear reproduction steps or proof-of-concept
   - Impact assessment and suggested remediation (if known)
3. We aim to acknowledge reports within **72 hours** and coordinate a fix before public disclosure.
