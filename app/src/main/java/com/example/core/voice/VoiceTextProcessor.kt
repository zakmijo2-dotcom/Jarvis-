package com.example.core.voice

import java.util.Locale

object VoiceLanguageDetector {

    private val algerianArabicMarkers = listOf(
        "واش", "علاش", "بزاف", "راك", "راني", "كاين", "ماكاش", "تاع", "دوك", "مليح", "صحا", "خويا", "كيفاش", "وين"
    )
    private val algerianLatinMarkers = setOf(
        "wach", "wesh", "rak", "rani", "bzzaf", "bezzaf", "kayen", "makach", "3lach", "kifach", "labes", "saha", "khouya"
    )
    private val frenchMarkers = setOf(
        "bonjour", "salut", "merci", "comment", "pourquoi", "avec", "dans", "vous", "nous", "suis",
        "quel", "quelle", "demain", "aujourd", "oui", "non", "parle", "français", "francais", "est", "sont", "peux"
    )
    private val frenchDiacritics = charArrayOf('é', 'è', 'ê', 'ë', 'à', 'â', 'ù', 'û', 'ô', 'î', 'ï', 'ç', 'œ')

    fun detectLanguage(
        text: String,
        preferred: VoiceLanguageOption,
        systemLocale: Locale = Locale.getDefault()
    ): VoiceLanguageOption {
        if (preferred != VoiceLanguageOption.AUTO) {
            return preferred
        }
        val clean = text.trim()
        if (clean.isEmpty()) {
            return fromSystemLocale(systemLocale)
        }

        // 1. Check Arabic script Unicode blocks
        val arabicCharCount = clean.count { ch ->
            ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\u08A0'..'\u08FF'
        }
        if (arabicCharCount >= 2 || (arabicCharCount > 0 && arabicCharCount * 3 >= clean.length)) {
            if (algerianArabicMarkers.any { clean.contains(it) } ||
                systemLocale.country.equals("DZ", ignoreCase = true)
            ) {
                return VoiceLanguageOption.ARABIC_ALGERIAN
            }
            return VoiceLanguageOption.ARABIC
        }

        val lower = clean.lowercase(Locale.ROOT)
        val tokens = lower.split(Regex("[^\\p{L}0-9']+")).filter { it.isNotEmpty() }.toSet()

        // 2. Check Romanized Algerian Darija markers
        if (tokens.any { it in algerianLatinMarkers }) {
            return VoiceLanguageOption.ARABIC_ALGERIAN
        }

        // 3. Check French diacritics or common French words
        val hasFrenchAccent = lower.any { it in frenchDiacritics }
        val frenchHits = tokens.count { it in frenchMarkers }
        if (hasFrenchAccent || frenchHits >= 1) {
            return VoiceLanguageOption.FRENCH
        }

        return if (systemLocale.language.equals("fr", ignoreCase = true)) {
            VoiceLanguageOption.FRENCH
        } else {
            VoiceLanguageOption.ENGLISH
        }
    }

    private fun fromSystemLocale(locale: Locale): VoiceLanguageOption {
        return when (locale.language.lowercase(Locale.ROOT)) {
            "ar" -> if (locale.country.equals("DZ", ignoreCase = true)) {
                VoiceLanguageOption.ARABIC_ALGERIAN
            } else {
                VoiceLanguageOption.ARABIC
            }
            "fr" -> VoiceLanguageOption.FRENCH
            else -> VoiceLanguageOption.ENGLISH
        }
    }
}

object TtsSanitizer {

    private val fencedCodeBlockRegex = Regex("```[\\s\\S]*?```")
    private val markdownLinkRegex = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
    private val urlRegex = Regex("https?://\\S+")
    private val markdownHeadingRegex = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val blockquoteRegex = Regex("(?m)^\\s*>\\s*")
    private val inlineCodeRegex = Regex("`([^`]*)`")
    private val telemetrySuffixRegex = Regex(
        "(?i)\\b(TTFT|tok/s|tokens/sec|prefill|output tokens|native heap|GPU \\(OpenCL\\)|CPU \\(XNNPACK\\))\\b[^\\n]*"
    )
    private val bracketNoticeRegex = Regex("\\[(Generation stopped|Model Error|System Notice)\\]", RegexOption.IGNORE_CASE)

    fun sanitizeForSpeech(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
            .replace("\r\n", "\n")
            .replace(fencedCodeBlockRegex, " Code block omitted. ")
            .replace(markdownLinkRegex, "$1")
            .replace(urlRegex, "")
            .replace(bracketNoticeRegex, "")
            .replace(telemetrySuffixRegex, "")
            .replace(markdownHeadingRegex, "")
            .replace(blockquoteRegex, "")
            .replace(inlineCodeRegex, "$1")
            .replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            .replace(Regex("(?m)^\\s*[-*•+]\\s+"), "")
            .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
            .replace(Regex("[*_~#|<>{}\\[\\]═─]+"), " ")
            .replace("//", " ")

        text = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(". ")
            .replace("..", ".")
            .replace(" .", ".")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        return text
    }

    fun chunkForTts(cleanText: String, maxChunkChars: Int = 1200): List<String> {
        val text = cleanText.trim()
        if (text.isEmpty()) return emptyList()
        if (text.length <= maxChunkChars) return listOf(text)

        val chunks = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[.!?؟])\\s+"))
        val current = StringBuilder()

        for (sentence in sentences) {
            val s = sentence.trim()
            if (s.isEmpty()) continue
            if (current.isNotEmpty() && current.length + s.length + 1 > maxChunkChars) {
                chunks.add(current.toString().trim())
                current.setLength(0)
            }
            if (s.length > maxChunkChars) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.setLength(0)
                }
                var idx = 0
                while (idx < s.length) {
                    val end = (idx + maxChunkChars).coerceAtMost(s.length)
                    chunks.add(s.substring(idx, end).trim())
                    idx = end
                }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(s)
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString().trim())
        }
        return chunks
    }
}
