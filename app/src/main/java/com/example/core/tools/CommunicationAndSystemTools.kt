package com.example.core.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import com.example.core.memory.LocalMemory
import com.example.core.memory.MemoryKind
import com.example.core.security.PromptSecuritySanitizer
import com.example.core.skills.AgentSkill
import com.example.core.skills.SkillEngine
import com.example.core.skills.SkillStepSpec
import com.example.data.repository.JarvisSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
            val safeTitle = PromptSecuritySanitizer.sanitizeExternalText(item.title, 120)
            val safePreview = PromptSecuritySanitizer.sanitizeExternalText(item.maskedPreview, 180)
            "• [$time] ${item.appLabel} — $safeTitle: $safePreview"
        }
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = PromptSecuritySanitizer.wrapUntrustedBlock(
                sourceLabel = "AndroidNotifications",
                rawContent = "Recent Notifications (${items.size}):\n$lines"
            ),
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

class InspectScreenElementsTool(
    private val inspector: (Int) -> List<ScreenElementSummary> = AccessibilityBridge::inspectActiveWindowElements
) : JarvisTool {
    override val name: String = "inspect_screen_elements"
    override val category: ToolCategory = ToolCategory.ACCESSIBILITY
    override val description: String = "Inspect visible, actionable UI elements in the active window on-demand (password fields are strictly redacted)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "max_elements",
            type = ToolParameterType.INTEGER,
            description = "Maximum number of UI elements to summarize (1-40).",
            required = false,
            minInt = 1,
            maxInt = 40
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = listOf(
        ToolPermissionRequirement.SpecialAccess(
            type = SpecialAccessType.ACCESSIBILITY_SERVICE,
            rationale = "JARVIS Accessibility Service must be enabled in Android Settings to inspect active window elements."
        )
    )
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val rawMax = args["max_elements"]?.trim().orEmpty()
        val maxElements = if (rawMax.isEmpty()) {
            20
        } else {
            rawMax.toIntOrNull() ?: return ToolValidationResult.Invalid("Parameter 'max_elements' must be an integer between 1 and 40.")
        }
        if (maxElements !in 1..40) {
            return ToolValidationResult.Invalid("Parameter 'max_elements' must be between 1 and 40.")
        }
        return ToolValidationResult.Valid(mapOf("max_elements" to maxElements.toString()))
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> =
        "Confirm inspecting visible UI elements on the current screen?" to
            "JARVIS inspects active window nodes strictly on-demand for this single request and redacts all password fields."

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val maxElements = args["max_elements"]?.toIntOrNull() ?: 20
        val elements = inspector(maxElements)
        if (elements.isEmpty()) {
            return ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "No visible UI elements could be retrieved from the active window. Ensure JARVIS Accessibility Service is connected.",
                verifiedFacts = mapOf(
                    "count" to "0",
                    "serviceConnected" to AccessibilityBridge.isServiceConnected.value.toString()
                ),
                requestArgs = args
            )
        }
        val lines = elements.joinToString("\n") { el ->
            val flags = buildList {
                if (el.isClickable) add("clickable")
                if (el.isEditable) add("editable")
                if (el.isScrollable) add("scrollable")
            }.joinToString(",")
            val flagSuffix = if (flags.isNotEmpty()) " [$flags]" else ""
            val safeLabel = if (el.label == "[REDACTED_PASSWORD]") "[REDACTED_PASSWORD]"
            else PromptSecuritySanitizer.sanitizeExternalText(el.label, 80)
            "• $safeLabel (${el.className}$flagSuffix) @ ${el.bounds}"
        }
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = PromptSecuritySanitizer.wrapUntrustedBlock(
                sourceLabel = "AccessibilityWindow",
                rawContent = "Active Window UI Elements (${elements.size}):\n$lines"
            ),
            verifiedFacts = mapOf(
                "count" to elements.size.toString(),
                "clickableCount" to elements.count { it.isClickable }.toString(),
                "editableCount" to elements.count { it.isEditable }.toString()
            ),
            requestArgs = args
        )
    }
}

class TapUiElementTool(
    private val tapper: (String) -> Boolean = AccessibilityBridge::tapUiElement
) : JarvisTool {
    override val name: String = "tap_ui_element"
    override val category: ToolCategory = ToolCategory.ACCESSIBILITY
    override val description: String = "Tap a visible UI element matching text, content description, or view ID."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "target",
            type = ToolParameterType.STRING,
            description = "Visible label, content description, or view ID of the UI element to tap."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = listOf(
        ToolPermissionRequirement.SpecialAccess(
            type = SpecialAccessType.ACCESSIBILITY_SERVICE,
            rationale = "JARVIS Accessibility Service must be enabled to tap UI elements."
        )
    )
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val target = args["target"]?.trim().orEmpty()
        if (target.isEmpty() || target.length > 100) {
            return ToolValidationResult.Invalid("Parameter 'target' is required (1-100 characters).")
        }
        return ToolValidationResult.Valid(mapOf("target" to target))
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> =
        "Confirm tapping UI element '${args["target"].orEmpty()}'?" to
            "JARVIS will perform a single authorized click/gesture on the matching screen element."

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val target = args.getValue("target")
        val ok = tapper(target)
        return if (ok) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Tapped UI element matching '$target'.",
                verifiedFacts = mapOf("target" to target, "tapped" to "true"),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "Could not find or tap a visible UI element matching '$target'.",
                verifiedFacts = mapOf("target" to target, "tapped" to "false"),
                requestArgs = args
            )
        }
    }
}

class ScrollScreenTool(
    private val scroller: (String) -> Boolean = AccessibilityBridge::scrollUiScreen
) : JarvisTool {
    override val name: String = "scroll_screen"
    override val category: ToolCategory = ToolCategory.ACCESSIBILITY
    override val description: String = "Scroll the active screen or scrollable container in a given direction (down, up, left, right)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "direction",
            type = ToolParameterType.ENUM,
            description = "Scroll direction.",
            allowedValues = listOf("down", "up", "left", "right")
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = listOf(
        ToolPermissionRequirement.SpecialAccess(
            type = SpecialAccessType.ACCESSIBILITY_SERVICE,
            rationale = "JARVIS Accessibility Service must be enabled to scroll the screen."
        )
    )
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val dir = args["direction"]?.trim()?.lowercase().orEmpty()
        if (dir !in setOf("down", "up", "left", "right")) {
            return ToolValidationResult.Invalid("Parameter 'direction' must be one of: down, up, left, right.")
        }
        return ToolValidationResult.Valid(mapOf("direction" to dir))
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> =
        "Confirm scrolling screen '${args["direction"].orEmpty()}'?" to
            "JARVIS will perform a single authorized scroll action on the active window."

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val direction = args.getValue("direction")
        val ok = scroller(direction)
        return if (ok) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Scrolled active screen $direction.",
                verifiedFacts = mapOf("direction" to direction, "scrolled" to "true"),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "Could not scroll active window ($direction). Ensure a scrollable view is active.",
                verifiedFacts = mapOf("direction" to direction, "scrolled" to "false"),
                requestArgs = args
            )
        }
    }
}

class InputUiTextTool(
    private val textInjector: (String?, String) -> Boolean = AccessibilityBridge::inputTextIntoUi
) : JarvisTool {
    override val name: String = "input_ui_text"
    override val category: ToolCategory = ToolCategory.ACCESSIBILITY
    override val description: String = "Enter text into an editable non-password UI field matching a target label or the currently focused input."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "text",
            type = ToolParameterType.STRING,
            description = "Text to enter into the editable UI field."
        ),
        ToolParameterSpec(
            name = "target",
            type = ToolParameterType.STRING,
            description = "Optional label, hint, or view ID of the target input field.",
            required = false
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = listOf(
        ToolPermissionRequirement.SpecialAccess(
            type = SpecialAccessType.ACCESSIBILITY_SERVICE,
            rationale = "JARVIS Accessibility Service must be enabled to enter text into UI fields."
        )
    )
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val text = args["text"]?.trim().orEmpty()
        if (text.isEmpty() || text.length > 500) {
            return ToolValidationResult.Invalid("Parameter 'text' is required (1-500 characters).")
        }
        val target = args["target"]?.trim().orEmpty()
        val normalized = buildMap {
            put("text", text)
            if (target.isNotEmpty()) put("target", target)
        }
        return ToolValidationResult.Valid(normalized)
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> {
        val target = args["target"]?.takeIf { it.isNotBlank() } ?: "focused editable field"
        return "Confirm entering text into $target?" to
            "JARVIS will set text on the matching editable field (password fields are strictly blocked)."
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val text = args.getValue("text")
        val target = args["target"]
        val ok = textInjector(target, text)
        return if (ok) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Entered text into ${target ?: "focused input field"}.",
                verifiedFacts = mapOf(
                    "target" to (target ?: "focused"),
                    "length" to text.length.toString()
                ),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "Could not enter text into ${target ?: "focused input field"} (field not found, not editable, or protected password field).",
                verifiedFacts = mapOf("target" to (target ?: "focused")),
                requestArgs = args
            )
        }
    }
}

class CreateReminderTool(
    private val intentLauncher: (Context, Intent) -> Boolean = ::launchIntentSafely
) : JarvisTool {
    override val name: String = "create_reminder"
    override val category: ToolCategory = ToolCategory.APPLICATION
    override val description: String = "Create a countdown timer or alarm reminder using standard Android Clock intents after user confirmation."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "label",
            type = ToolParameterType.STRING,
            description = "Reminder or timer title (1-80 characters)."
        ),
        ToolParameterSpec(
            name = "minutes",
            type = ToolParameterType.INTEGER,
            description = "Countdown duration in minutes (1-1440).",
            required = false,
            minInt = 1,
            maxInt = 1440
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val label = args["label"]?.trim().orEmpty()
        if (label.isEmpty() || label.length > 80) {
            return ToolValidationResult.Invalid("Parameter 'label' is required (1-80 characters).")
        }
        val minutesRaw = args["minutes"]?.trim().orEmpty()
        val minutes = if (minutesRaw.isEmpty()) 15 else minutesRaw.toIntOrNull()
            ?: return ToolValidationResult.Invalid("Parameter 'minutes' must be an integer between 1 and 1440.")
        if (minutes !in 1..1440) {
            return ToolValidationResult.Invalid("Parameter 'minutes' must be between 1 and 1440.")
        }
        return ToolValidationResult.Valid(mapOf("label" to label, "minutes" to minutes.toString()))
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> {
        val label = args["label"].orEmpty()
        val minutes = args["minutes"] ?: "15"
        return "Confirm creating Android reminder/timer '$label' in $minutes min?" to
            "Uses standard Android Clock intent (zero background permissions required)."
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val label = args.getValue("label")
        val minutes = args.getValue("minutes").toInt()
        val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        val started = intentLauncher(context, timerIntent)
        return if (started) {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Opened Clock reminder/timer '$label' for $minutes minute(s).",
                verifiedFacts = mapOf("label" to label, "minutes" to minutes.toString()),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = "No Android Clock app handled ACTION_SET_TIMER on this device.",
                requestArgs = args
            )
        }
    }
}

class RetrieveMemoryTool(
    private val localMemoryProvider: () -> LocalMemory?
) : JarvisTool {
    override val name: String = "retrieve_memory"
    override val category: ToolCategory = ToolCategory.MEMORY
    override val description: String = "Search and rank user-approved persistent memories (preferences, project context, summaries, facts)."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "query",
            type = ToolParameterType.STRING,
            description = "Topic or keywords to retrieve from persistent Room memory."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val q = args["query"]?.trim().orEmpty()
        if (q.isEmpty() || q.length > 120) {
            return ToolValidationResult.Invalid("Parameter 'query' is required (1-120 characters).")
        }
        return ToolValidationResult.Valid(mapOf("query" to q))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val mem = localMemoryProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "LocalMemory is not bound.")
        val query = args.getValue("query")
        val matches = mem.retrieveRelevantContext(query, maxItems = 5)
        if (matches.isEmpty()) {
            return ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "No persistent memory entries matched '$query'.",
                verifiedFacts = mapOf("query" to query, "count" to "0"),
                requestArgs = args
            )
        }
        val formatted = matches.joinToString("\n") {
            "• [${it.kind.label} / ${it.category}] ${it.content}"
        }
        return ToolExecutionResult(
            toolName = name,
            status = ToolExecutionStatus.SUCCESS,
            summary = "Retrieved ${matches.size} memory record(s):\n$formatted",
            verifiedFacts = mapOf("query" to query, "count" to matches.size.toString()),
            requestArgs = args
        )
    }
}

class SaveMemoryTool(
    private val localMemoryProvider: () -> LocalMemory?
) : JarvisTool {
    override val name: String = "save_memory"
    override val category: ToolCategory = ToolCategory.MEMORY
    override val description: String = "Save a non-sensitive user preference, project context, or fact to persistent Room memory with duplicate checks."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "kind",
            type = ToolParameterType.ENUM,
            description = "Memory classification.",
            allowedValues = listOf("PREFERENCE", "PROJECT", "FACT", "CONTEXT")
        ),
        ToolParameterSpec(
            name = "category",
            type = ToolParameterType.STRING,
            description = "Short category label (e.g. 'Language', 'Workflow').",
            required = false
        ),
        ToolParameterSpec(
            name = "content",
            type = ToolParameterType.STRING,
            description = "Memory fact to persist (1-500 characters; secrets blocked)."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val kindRaw = args["kind"]?.trim()?.uppercase(Locale.ROOT).orEmpty().ifEmpty { "FACT" }
        if (kindRaw !in setOf("PREFERENCE", "PROJECT", "FACT", "CONTEXT")) {
            return ToolValidationResult.Invalid("Parameter 'kind' must be PREFERENCE, PROJECT, FACT, or CONTEXT.")
        }
        val content = args["content"]?.trim().orEmpty()
        if (content.isEmpty() || content.length > 500) {
            return ToolValidationResult.Invalid("Parameter 'content' is required (1-500 characters).")
        }
        val category = args["category"]?.trim().orEmpty().ifEmpty { kindRaw }
        return ToolValidationResult.Valid(mapOf("kind" to kindRaw, "category" to category, "content" to content))
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val mem = localMemoryProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "LocalMemory is not bound.")
        val kind = MemoryKind.fromRaw(args.getValue("kind"))
        val res = mem.saveMemory(
            kind = kind,
            category = args.getValue("category"),
            content = args.getValue("content"),
            userApproved = true,
            containsSensitiveConsent = false
        )
        return if (res.isSuccess) {
            val saved = res.getOrThrow()
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Persisted ${saved.kind.label} memory (${saved.category}, score=${saved.importanceScore}).",
                verifiedFacts = mapOf("memoryId" to saved.id, "kind" to saved.kind.name),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = res.exceptionOrNull()?.localizedMessage ?: "Failed to save memory.",
                requestArgs = args
            )
        }
    }
}

class SaveSkillTool(
    private val skillEngineProvider: () -> SkillEngine?,
    private val registryProvider: () -> ToolRegistry?
) : JarvisTool {
    override val name: String = "save_skill"
    override val category: ToolCategory = ToolCategory.MEMORY
    override val description: String = "Save a safe, reusable procedural workflow skill (battery/device checks, settings shortcuts) to the Skills Library."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "name",
            type = ToolParameterType.STRING,
            description = "Unique snake_case skill identifier (e.g. 'daily_health_check')."
        ),
        ToolParameterSpec(
            name = "description",
            type = ToolParameterType.STRING,
            description = "Short explanation of what the workflow achieves."
        ),
        ToolParameterSpec(
            name = "trigger",
            type = ToolParameterType.STRING,
            description = "Comma-separated trigger phrases (e.g. 'daily check, morning audit')."
        ),
        ToolParameterSpec(
            name = "tool_sequence",
            type = ToolParameterType.STRING,
            description = "Comma-separated safe registered tool names (e.g. 'read_battery_status,read_device_info')."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = true

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val skillName = args["name"]?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val desc = args["description"]?.trim().orEmpty()
        val trigger = args["trigger"]?.trim().orEmpty()
        val seq = args["tool_sequence"]?.trim().orEmpty()
        if (skillName.length < 2 || desc.isEmpty() || seq.isEmpty()) {
            return ToolValidationResult.Invalid("Parameters 'name', 'description', and 'tool_sequence' are required.")
        }
        return ToolValidationResult.Valid(
            mapOf(
                "name" to skillName,
                "description" to desc,
                "trigger" to trigger.ifBlank { skillName.replace('_', ' ') },
                "tool_sequence" to seq
            )
        )
    }

    override fun describeConfirmation(args: Map<String, String>): Pair<String, String> =
        "Save reusable workflow skill '${args["name"].orEmpty()}'?" to
            "JARVIS validates every tool step and blocks dangerous or destructive actions from automatic skill persistence."

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val engine = skillEngineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "SkillEngine is not bound.")
        val registry = registryProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "ToolRegistry is not bound.")
        val toolNames = args.getValue("tool_sequence")
            .split(',')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
        val steps = toolNames.map { tName ->
            if (registry.getTool(tName) == null) {
                return ToolExecutionResult(name, ToolExecutionStatus.VALIDATION_ERROR, "Unknown tool '$tName' in skill sequence.")
            }
            SkillStepSpec(toolName = tName)
        }
        val res = engine.saveSkill(
            AgentSkill(
                id = UUID.randomUUID().toString(),
                name = args.getValue("name"),
                description = args.getValue("description"),
                triggerPattern = args.getValue("trigger"),
                steps = steps,
                provenance = "Explicit Operator Tool"
            ),
            allowHighRiskManualSave = false
        )
        return if (res.isSuccess) {
            val saved = res.getOrThrow()
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.SUCCESS,
                summary = "Saved reusable skill '${saved.name}' v${saved.version} (${saved.steps.size} step(s)).",
                verifiedFacts = mapOf("skillName" to saved.name, "version" to saved.version.toString()),
                requestArgs = args
            )
        } else {
            ToolExecutionResult(
                toolName = name,
                status = ToolExecutionStatus.FAILED,
                summary = res.exceptionOrNull()?.localizedMessage ?: "Failed to save skill.",
                requestArgs = args
            )
        }
    }
}

class RunSkillTool(
    private val skillEngineProvider: () -> SkillEngine?,
    private val registryProvider: () -> ToolRegistry?
) : JarvisTool {
    override val name: String = "run_skill"
    override val category: ToolCategory = ToolCategory.MEMORY
    override val description: String = "Execute a saved multi-step procedural skill from the Skills Library and verify every step."
    override val parameters: List<ToolParameterSpec> = listOf(
        ToolParameterSpec(
            name = "name",
            type = ToolParameterType.STRING,
            description = "Exact skill name to execute."
        )
    )
    override val permissionRequirements: List<ToolPermissionRequirement> = emptyList()
    override val requiresUserConfirmation: Boolean = false

    override fun validate(args: Map<String, String>): ToolValidationResult {
        val nameArg = args["name"]?.trim().orEmpty()
        if (nameArg.isEmpty()) return ToolValidationResult.Invalid("Parameter 'name' is required.")
        val normalized = linkedMapOf<String, String>()
        args.forEach { (k, v) -> normalized[k.trim()] = v.trim() }
        normalized["name"] = nameArg
        return ToolValidationResult.Valid(normalized)
    }

    override suspend fun execute(context: Context, args: Map<String, String>): ToolExecutionResult {
        val engine = skillEngineProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "SkillEngine is not bound.")
        val registry = registryProvider()
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "ToolRegistry is not bound.")
        val skill = engine.getSkillByName(args.getValue("name"))
            ?: return ToolExecutionResult(name, ToolExecutionStatus.FAILED, "Skill '${args.getValue("name")}' was not found.")
        val report = engine.executeSkill(
            context = context,
            skill = skill,
            providedParams = args,
            toolRegistry = registry,
            settings = JarvisSettings(),
            userConfirmed = false
        )
        return ToolExecutionResult(
            toolName = name,
            status = if (report.overallSuccess) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.FAILED,
            summary = report.summary,
            verifiedFacts = mapOf(
                "skillName" to skill.name,
                "completedSteps" to report.completedSteps.toString(),
                "totalSteps" to report.totalSteps.toString()
            ),
            requestArgs = args
        )
    }
}


