package com.example.core.security

/**
 * Configurable package blocklist protecting banking, authenticator, payment, and installer
 * apps from autonomous control, accessibility scraping, or visual screenshot upload.
 */
object AppBlocklistPolicy {

    val DEFAULT_BLOCKED_PACKAGES: Set<String> = setOf(
        // Password managers & authenticators
        "com.agilebits.onepassword",
        "com.x8bit.bitwarden",
        "com.lastpass.lpandroid",
        "keepass2android.keepass2android",
        "com.google.android.apps.authenticator2",
        "com.authy.authy",
        "com.duosecurity.duomobile",
        "com.microsoft.azureauthenticator",
        // Banking & financial wallets
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.coinbase.android",
        "com.binance.dev",
        "com.chase.sig.android",
        "com.infonow.bofa",
        "com.wf.wellsfargomobile",
        "com.citi.citimobile",
        "com.revolut.revolut",
        "com.wise.transfer",
        "com.google.android.apps.walletnfcrel",
        // System credential / installer guards
        "com.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    private val SENSITIVE_PACKAGE_KEYWORDS = listOf(
        "bank", "wallet", "authenticator", "keepass", "bitwarden",
        "1password", "lastpass", "crypto", "binance", "coinbase"
    )

    fun parseCsvPackages(csv: String?): Set<String> {
        if (csv.isNullOrBlank()) return emptySet()
        return csv.split(',', '\n', ';')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && '.' in it }
            .toSet()
    }

    fun isPackageBlocked(
        packageName: String?,
        customBlockedCsv: String? = null
    ): Boolean {
        val pkg = packageName?.trim()?.lowercase().orEmpty()
        if (pkg.isEmpty()) return false
        if (pkg in DEFAULT_BLOCKED_PACKAGES) return true
        val custom = parseCsvPackages(customBlockedCsv)
        if (pkg in custom) return true
        // Heuristic safeguard on obvious banking / authenticator apps unless JARVIS itself
        if (pkg == "com.aistudio.jarvis.k8v4mz" || pkg == "com.example") return false
        return SENSITIVE_PACKAGE_KEYWORDS.any { kw -> pkg.contains(kw) }
    }

    fun mergeDisplayList(customBlockedCsv: String?): List<String> =
        (DEFAULT_BLOCKED_PACKAGES + parseCsvPackages(customBlockedCsv))
            .distinct()
            .sorted()
}
