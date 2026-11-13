package com.example.core.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContactMatch(
    val displayName: String,
    val phoneNumber: String
)

class SearchContactsTool(
    private val contactQueryProvider: (Context, String) -> List<ContactMatch> = ::queryDeviceContacts
) : JarvisTool {
    override val name: String = "search_contacts"
    override val category: ToolCategory = ToolCategory.COMMUNICATION
    override val description: String = "Search device contacts by name or phone number (requires READ_CONTACTS)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "query",
            type = ToolParameterType.STRING,
            description = "Contact name or partial phone number to look up."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = listOf(
        ToolPermissionRequirement.RuntimePermission(
            permission = Manifest.permission.READ_CONTACTS,
            rationale = "READ_CONTACTS permission is required to search your device address book."
        )
    )
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val query = args["query"]?.trim().orEmpty()
        if (query.isEmpty() || query.length > 80) {
            return ToolValidationResult.Invalid("Parameter 'query' must be between 1 and 80 characters.")
        }
        return ToolValidationResult.Valid(mapOf("query" to query))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val query = args.getValue("query")
        val matches = contactQueryProvider(context, query)
        return if (matches.isEmpty()) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "No contacts matched '$query'.",
                verifiedFacts = mapOf("query" to query, "matchCount" to "0"),
                requestArgs = args
            )
        } else {
            val formatted = matches.joinToString("\n") { "• ${it.displayName}: ${it.phoneNumber}" }
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Found ${matches.size} contact(s) matching '$query':\n$formatted",
                verifiedFacts = mapOf(
                    "query" to query,
                    "matchCount" to matches.size.toString(),
                    "topContact" to "${matches.first().displayName} (${matches.first().phoneNumber})"
                ),
                requestArgs = args
            )
        }
    }

    companion object {
        fun queryDeviceContacts(context: Context, query: String): List<ContactMatch> {
            val results = mutableListOf<ContactMatch>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val pattern = "%$query%"
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                arrayOf(pattern, pattern),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext() && results.size < 10) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else ""
                    val num = if (numIdx >= 0) c.getString(numIdx).orEmpty() else ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        results.add(ContactMatch(name.trim(), num.trim()))
                    }
                }
            }
            return results.distinctBy { "${it.displayName}|${it.phoneNumber}" }
        }
    }
}

class InitiatePhoneCallTool(
    private val intentLauncher: (Context, Intent) -> Boolean = ::launchIntentSafely
) : JarvisTool {
    override val name: String = "initiate_phone_call"
    override val category: ToolCategory = ToolCategory.COMMUNICATION
    override val description: String = "Open the Android dialer pre-filled with a verified phone number after explicit user confirmation."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "phone_number",
            type = ToolParameterType.STRING,
            description = "Destination phone number (digits, +, spaces, hyphens, parentheses)."
        ),
        ToolParameterSpec(
            name = "contact_name",
            type = ToolParameterType.STRING,
            description = "Optional display name for confirmation banner.",
            required = false
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val phone = args["phone_number"]?.trim().orEmpty()
        if (!PHONE_REGEX.matches(phone)) {
            return ToolValidationResult.Invalid(
                "Invalid phone number '$phone'. Expected 3-20 phone characters (digits, +, -, spaces, parentheses)."
            )
        }
        val cleaned = linkedMapOf("phone_number" to phone)
        args["contact_name"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            cleaned["contact_name"] = it.take(60)
        }
        return ToolValidationResult.Valid(cleaned)
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> {
        val number = args["phone_number"].orEmpty()
        val label = args["contact_name"]?.let { "$it ($number)" } ?: number
        return "Confirm initiating phone call to $label?" to
            "JARVIS will open the Android Phone dialer (ACTION_DIAL) with this number. No call is placed silently."
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val number = args.getValue("phone_number")
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        val started = intentLauncher(context, dialIntent)
        return if (started) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Verified dialer handoff opened for $number.",
                verifiedFacts = mapOf("recipient" to number, "intentAction" to Intent.ACTION_DIAL),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "No Phone dialer application resolved ACTION_DIAL for '$number' on this device.",
                verifiedFacts = mapOf("recipient" to number),
                requestArgs = args
            )
        }
    }

    companion object {
        private val PHONE_REGEX = Regex("^[+]?[0-9\\s()-]{3,20}$")
    }
}

class ComposeMessageTool(
    private val intentLauncher: (Context, Intent) -> Boolean = ::launchIntentSafely
) : JarvisTool {
    override val name: String = "compose_message"
    override val category: ToolCategory = ToolCategory.COMMUNICATION
    override val description: String = "Prepare an SMS/text message draft via Android ACTION_SENDTO after explicit user confirmation."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "recipient",
            type = ToolParameterType.STRING,
            description = "Recipient phone number."
        ),
        ToolParameterSpec(
            name = "message_body",
            type = ToolParameterType.STRING,
            description = "Message body text (1-500 characters)."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val recipient = args["recipient"]?.trim().orEmpty()
        val body = args["message_body"]?.trim().orEmpty()
        if (!RECIPIENT_REGEX.matches(recipient)) {
            return ToolValidationResult.Invalid("Invalid recipient number '$recipient'.")
        }
        if (body.isEmpty() || body.length > 500) {
            return ToolValidationResult.Invalid("Parameter 'message_body' must be between 1 and 500 characters.")
        }
        return ToolValidationResult.Valid(
            mapOf("recipient" to recipient, "message_body" to body)
        )
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> {
        val recipient = args["recipient"].orEmpty()
        val body = args["message_body"].orEmpty()
        return "Confirm opening SMS composer to $recipient?\n\"$body\"" to
            "JARVIS never sends SMS messages silently in the background. Your default messaging app will open for final send review."
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val recipient = args.getValue("recipient")
        val body = args.getValue("message_body")
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipient)}")).apply {
            putExtra("sms_body", body)
        }
        val started = intentLauncher(context, smsIntent)
        return if (started) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Verified SMS composer opened for $recipient (${body.length} chars).",
                verifiedFacts = mapOf(
                    "recipient" to recipient,
                    "bodyLength" to body.length.toString(),
                    "intentAction" to Intent.ACTION_SENDTO
                ),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "No Messaging/SMS app resolved ACTION_SENDTO for '$recipient' on this device.",
                verifiedFacts = mapOf("recipient" to recipient),
                requestArgs = args
            )
        }
    }

    companion object {
        private val RECIPIENT_REGEX = Regex("^[+]?[0-9\\s()-]{3,20}$")
    }
}

class ReadNotificationsTool : JarvisTool {
    override val name: String = "read_notifications"
    override val category: ToolCategory = ToolCategory.NOTIFICATIONS
    override val description: String = "Read recent notifications captured by NotificationListenerService with privacy masking controls."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "limit",
            type = ToolParameterType.INTEGER,
            description = "Maximum notifications to return (1-20).",
            required = false,
            minInt = 1,
            maxInt = 20
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = listOf(
        ToolPermissionRequirement.SpecialAccess(
            type = SpecialAccessType.NOTIFICATION_LISTENER,
            rationale = "Notification Access must be explicitly granted in Android Settings before JARVIS can read notifications."
        )
    )
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val rawLimit = args["limit"]?.trim()?.takeIf { it.isNotEmpty() } ?: "5"
        val limit = rawLimit.toIntOrNull()
            ?: return ToolValidationResult.Invalid("Parameter 'limit' must be an integer between 1 and 20.")
        if (limit !in 1..20) {
            return ToolValidationResult.Invalid("Parameter 'limit' must be between 1 and 20.")
        }
        return ToolValidationResult.Valid(mapOf("limit" to limit.toString()))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val limit = args.getValue("limit").toInt()
        val items = NotificationBridge.notifications.value.take(limit)
        if (items.isEmpty()) {
            return ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Notification buffer is empty (no eligible recent notifications captured while enabled).",
                verifiedFacts = mapOf(
                    "count" to "0",
                    "serviceConnected" to NotificationBridge.isServiceConnected.value.toString()
                ),
                requestArgs = args
            )
        }
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val lines = items.joinToString("\n") { item ->
            val time = formatter.format(Date(item.postedAtMillis))
            "• [$time] ${item.appLabel} — ${item.title}: ${item.maskedPreview}"
        }
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = "Recent Notifications (${items.size}):\n$lines",
            verifiedFacts = mapOf(
                "count" to items.size.toString(),
                "privacyMasked" to items.any { it.rawContentRedacted }.toString()
            ),
            requestArgs = args
        )
    }
}

class AccessibilityActionTool(
    private val accessibilityRunner: (AuthorizedAccessibilityAction) -> Boolean = AccessibilityBridge::performAuthorizedGlobalAction
) : JarvisTool {
    override val name: String = "accessibility_global_action"
    override val category: ToolCategory = ToolCategory.ACCESSIBILITY
    override val description: String = "Perform an authorized Android global navigation action (home, back, recents, notifications, quick_settings, lock_screen)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "action",
            type = ToolParameterType.ENUM,
            description = "Authorized global navigation action.",
            allowedValues = AuthorizedAccessibilityAction.entries.map { it.actionKey }
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = listOf(
        ToolPermissionRequirement.SpecialAccess(
            type = SpecialAccessType.ACCESSIBILITY_SERVICE,
            rationale = "JARVIS Accessibility Service must be explicitly enabled in Android Accessibility Settings."
        )
    )
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val rawAction = args["action"]?.trim().orEmpty()
        val parsed = AuthorizedAccessibilityAction.fromKey(rawAction)
            ?: return ToolValidationResult.Invalid(
                "Unsupported accessibility action '$rawAction'. Valid actions: " +
                    AuthorizedAccessibilityAction.entries.joinToString(", ") { it.actionKey }
            )
        return ToolValidationResult.Valid(mapOf("action" to parsed.actionKey))
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> {
        val parsed = AuthorizedAccessibilityAction.fromKey(args["action"].orEmpty())
        val label = parsed?.label ?: args["action"].orEmpty()
        return "Confirm executing Accessibility action: $label?" to
            "JARVIS AccessibilityService only performs explicit global navigation actions and never reads or collects screen contents."
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val parsed = AuthorizedAccessibilityAction.fromKey(args.getValue("action"))
            ?: return ToolExecutionResult(name, ToolExecutionStatus.VALIDATION_ERROR, "Invalid action.")
        val success = accessibilityRunner(parsed)
        return if (success) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Executed Accessibility global action: ${parsed.label}.",
                verifiedFacts = mapOf("action" to parsed.actionKey, "label" to parsed.label),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "Accessibility service did not confirm action '${parsed.label}'. Ensure JARVIS Accessibility Service is active in Settings.",
                verifiedFacts = mapOf(
                    "action" to parsed.actionKey,
                    "serviceConnected" to AccessibilityBridge.isServiceConnected.value.toString()
                ),
                requestArgs = args
            )
        }
    }
}
