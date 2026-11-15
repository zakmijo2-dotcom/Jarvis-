package com.example.core.security

/**
 * Redacts credentials, Bearer tokens, API keys, and 6-digit Wireless ADB pairing codes
 * from error messages, network diagnostics, and execution logs.
 */
object SecurityLogRedactor {

    private val BEARER_REGEX = Regex("""(?i)(Bearer\s+)[A-Za-z0-9._\-~+/=]{6,}""")
    private val HEADER_KEY_REGEX = Regex("""(?i)((?:x-goog-api-key|x-api-key|api[_-]?key|token)\s*[:=]\s*|authorization\s*[:=]\s*(?!Bearer\b))([^\s,;]+)""")
    private val PREFIXED_SECRET_REGEX = Regex("""\b(hf_[A-Za-z0-9]{6,}|AIza[0-9A-Za-z\-_]{12,}|sk-[A-Za-z0-9\-_]{10,})\b""")
    private val PAIRING_CODE_REGEX = Regex("""(?i)(code|pin|pair(?:ing)?(?:\s+code)?)\s*[:=#]?\s*\d{6}\b""")

    fun redact(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var clean = raw
        clean = BEARER_REGEX.replace(clean) { m -> "${m.groupValues[1]}[REDACTED]" }
        clean = HEADER_KEY_REGEX.replace(clean) { m -> "${m.groupValues[1]}[REDACTED]" }
        clean = PREFIXED_SECRET_REGEX.replace(clean) { m ->
            val v = m.value
            "${v.take(4)}••••"
        }
        clean = PAIRING_CODE_REGEX.replace(clean) { m ->
            "${m.groupValues[1]} ••••••"
        }
        return clean
    }
}
