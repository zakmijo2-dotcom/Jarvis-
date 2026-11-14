package com.example.core.tools

enum class ToolCategory(val label: String) {
    APPLICATION("Application Control"),
    DEVICE("Device Control"),
    COMMUNICATION("Communication"),
    NOTIFICATIONS("Notifications"),
    ACCESSIBILITY("Accessibility")
}

enum class ToolRiskLevel(val label: String) {
    LOW("Low Risk"),
    MEDIUM("Medium Risk"),
    HIGH("High Risk")
}

enum class ToolParameterType {
    STRING,
    INTEGER,
    ENUM
}

data class ToolParameterSpec(
    val name: String,
    val type: ToolParameterType,
    val description: String,
    val required: Boolean = true,
    val allowedValues: List<String> = emptyList(),
    val minInt: Int? = null,
    val maxInt: Int? = null
)

enum class SpecialAccessType(val label: String, val settingsAction: String) {
    NOTIFICATION_LISTENER(
        label = "Notification Access",
        settingsAction = android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    ),
    ACCESSIBILITY_SERVICE(
        label = "Accessibility Service",
        settingsAction = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
    )
}

sealed interface ToolPermissionRequirement {
    data class RuntimePermission(
        val permission: String,
        val rationale: String
    ) : ToolPermissionRequirement

    data class SpecialAccess(
        val type: SpecialAccessType,
        val rationale: String
    ) : ToolPermissionRequirement
}

sealed interface ToolValidationResult {
    data class Valid(val normalizedArgs: Map<String, String>) : ToolValidationResult
    data class Invalid(val reason: String) : ToolValidationResult
}

sealed interface PermissionValidationResult {
    data object Granted : PermissionValidationResult
    data class MissingRuntimePermission(
        val permission: String,
        val rationale: String
    ) : PermissionValidationResult

    data class MissingSpecialAccess(
        val accessType: SpecialAccessType,
        val rationale: String
    ) : PermissionValidationResult

    data class OptInDisabled(
        val settingKey: String,
        val rationale: String
    ) : PermissionValidationResult
}

enum class ToolExecutionStatus {
    SUCCESS,
    CONFIRMATION_REQUIRED,
    PERMISSION_DENIED,
    VALIDATION_ERROR,
    CANCELLED,
    FAILED
}

data class ToolExecutionResult(
    val toolName: String,
    val status: ToolExecutionStatus,
    val summary: String,
    val verifiedFacts: Map<String, String> = emptyMap(),
    val missingRuntimePermission: String? = null,
    val missingSpecialAccess: SpecialAccessType? = null,
    val disabledOptInSetting: String? = null,
    val confirmationPrompt: String? = null,
    val requestArgs: Map<String, String> = emptyMap(),
    val executedAtMillis: Long = System.currentTimeMillis()
)

data class ToolCallRequest(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap(),
    val source: String = "Agent"
)

data class PendingToolConfirmation(
    val request: ToolCallRequest,
    val toolDescription: String,
    val confirmationMessage: String,
    val sensitivityWarning: String,
    val origin: String
)

data class CapturedNotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val maskedPreview: String,
    val rawContentRedacted: Boolean,
    val postedAtMillis: Long
)
