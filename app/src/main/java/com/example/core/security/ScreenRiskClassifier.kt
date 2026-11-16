package com.example.core.security

enum class ActionRiskTier {
    SAFE,
    CONFIRM_REQUIRED,
    BLOCKED
}

data class ScreenRiskAssessment(
    val tier: ActionRiskTier,
    val reason: String,
    val matchedSignals: List<String> = emptyList()
)

/**
 * Classifies active screen context and proposed phone-use actions before dispatch.
 * Clean-room policy matrix inspired by OpenPhoneUse RiskGuard and OpenPhone capability audits.
 */
object ScreenRiskClassifier {

    private val HIGH_RISK_TERMS = listOf(
        "pay", "purchase", "buy now", "checkout", "place order", "confirm payment",
        "transfer", "send money", "wire", "withdraw", "delete account", "factory reset",
        "uninstall", "erase all", "format", "grant all", "allow all", "authorize",
        "إرسال المال", "تحويل", "دفع", "شراء", "حذف الحساب", "إلغاء التثبيت",
        "payer", "acheter", "virement", "supprimer"
    )

    private val COMMUNICATION_SEND_TERMS = listOf(
        "send", "post", "publish", "submit", "reply", "call", "dial",
        "إرسال", "نشر", "اتصال", "envoyer", "publier", "appeler"
    )

    private val SENSITIVE_AUTH_TERMS = listOf(
        "[redacted_password]", "password", "passcode", "pin", "otp", "2fa",
        "verification code", "secret key", "seed phrase", "recovery phrase",
        "كلمة المرور", "رمز التحقق", "mot de passe"
    )

    fun assessAction(
        activePackage: String?,
        targetLabelOrQuery: String?,
        screenTextSample: String?,
        toolName: String,
        customBlockedCsv: String? = null
    ): ScreenRiskAssessment {
        if (AppBlocklistPolicy.isPackageBlocked(activePackage, customBlockedCsv)) {
            return ScreenRiskAssessment(
                tier = ActionRiskTier.BLOCKED,
                reason = "Blocked sensitive app package '${activePackage ?: "unknown"}'. Autonomous control and screenshot upload are disabled here.",
                matchedSignals = listOf("blocked_package:$activePackage")
            )
        }

        val combinedTarget = (targetLabelOrQuery.orEmpty() + " " + toolName).lowercase()
        val combinedScreen = screenTextSample.orEmpty().lowercase()

        val authHits = SENSITIVE_AUTH_TERMS.filter {
            combinedTarget.contains(it) || combinedScreen.contains(it)
        }
        if (authHits.isNotEmpty() && (toolName.contains("input") || toolName.contains("tap") || toolName.contains("capture_screen_visual"))) {
            return ScreenRiskAssessment(
                tier = ActionRiskTier.CONFIRM_REQUIRED,
                reason = "Screen contains sensitive credential or OTP fields (${authHits.take(2).joinToString(", ")}). Explicit approval required.",
                matchedSignals = authHits
            )
        }

        val highHits = HIGH_RISK_TERMS.filter { combinedTarget.contains(it) }
        if (highHits.isNotEmpty()) {
            return ScreenRiskAssessment(
                tier = ActionRiskTier.CONFIRM_REQUIRED,
                reason = "Irreversible or financial action detected (${highHits.take(2).joinToString(", ")}).",
                matchedSignals = highHits
            )
        }

        val sendHits = COMMUNICATION_SEND_TERMS.filter { combinedTarget.contains(it) }
        if (sendHits.isNotEmpty() && (toolName == "tap_ui_element" || toolName == "tap_coordinates" || toolName == "compose_message" || toolName == "initiate_phone_call")) {
            return ScreenRiskAssessment(
                tier = ActionRiskTier.CONFIRM_REQUIRED,
                reason = "Outbound communication or submission action (${sendHits.first()}). Explicit approval required.",
                matchedSignals = sendHits
            )
        }

        return ScreenRiskAssessment(
            tier = ActionRiskTier.SAFE,
            reason = "Low-risk UI observation or navigation action."
        )
    }
}
