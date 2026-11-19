package com.example.core.memory

import com.example.core.security.AppBlocklistPolicy
import com.example.core.vision.ScreenClassifier
import com.example.core.vision.ScreenObservation
import com.example.data.local.db.AppKnowledgeDao
import com.example.data.local.db.AppKnowledgeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

data class AppKnowledgeRecord(
    val id: String,
    val packageName: String,
    val appName: String,
    val screenSignature: String,
    val screenType: String,
    val knownElements: List<String>,
    val verifiedWorkflowHints: String,
    val successCount: Int,
    val failureCount: Int,
    val lastVerifiedAt: Long
)

/**
 * Application Knowledge Base (Stage 6).
 *
 * Persists facts-only learned UI patterns, interactive element labels, and verified navigation
 * hints per Android package so JARVIS improves reliability across repeated app workflows without
 * relying on fragile hardcoded pixel coordinates.
 */
class AppKnowledgeRepository(
    private val dao: AppKnowledgeDao
) {
    fun observeAll(): Flow<List<AppKnowledgeRecord>> =
        dao.observeAll().map { list -> list.map(::toRecord) }

    suspend fun getAllOnce(): List<AppKnowledgeRecord> =
        dao.getAllOnce().map(::toRecord)

    suspend fun recordVerifiedObservation(
        observation: ScreenObservation,
        appNameHint: String? = null,
        workflowHint: String = "",
        succeeded: Boolean = true
    ): AppKnowledgeRecord? {
        val pkg = observation.activePackageName?.trim().orEmpty()
        if (pkg.isBlank() || observation.blockedByPolicy || AppBlocklistPolicy.isPackageBlocked(pkg)) {
            return null
        }

        val classification = ScreenClassifier.classify(observation)
        val screenSig = "${classification.screenType.name}:${observation.stabilityHash.take(12)}"
        val topLabels = observation.elements
            .filter { it.clickable || it.editable }
            .mapNotNull { el ->
                val lbl = el.label.trim().take(48)
                val idPart = el.id.orEmpty().substringAfterLast('/').take(32)
                when {
                    lbl.isNotBlank() && idPart.isNotBlank() -> "$lbl [$idPart]"
                    lbl.isNotBlank() -> lbl
                    idPart.isNotBlank() -> "[$idPart]"
                    else -> null
                }
            }
            .distinct()
            .take(14)

        val cleanHint = workflowHint.trim().take(240)
        if (RoomLocalMemory.containsSensitiveInformation(cleanHint)) {
            return null
        }

        val existing = dao.findByPackageAndScreen(pkg, screenSig)
        val now = System.currentTimeMillis()
        val resolvedAppName = appNameHint?.trim()?.takeIf { it.isNotBlank() }
            ?: existing?.appName
            ?: inferFriendlyAppName(pkg)

        val mergedElements = ((existing?.let { parseStringList(it.knownElementsJson) }.orEmpty()) + topLabels)
            .distinct()
            .take(18)

        val mergedHints = buildString {
            val prev = existing?.verifiedWorkflowHints.orEmpty().trim()
            if (prev.isNotEmpty()) append(prev)
            if (cleanHint.isNotEmpty() && !prev.contains(cleanHint, ignoreCase = true)) {
                if (isNotEmpty()) append(" | ")
                append(cleanHint)
            }
        }.take(480)

        val entity = AppKnowledgeEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            packageName = pkg,
            appName = resolvedAppName,
            screenSignature = screenSig,
            screenType = classification.screenType.label,
            knownElementsJson = JSONArray(mergedElements).toString(),
            verifiedWorkflowHints = mergedHints,
            successCount = (existing?.successCount ?: 0) + if (succeeded) 1 else 0,
            failureCount = (existing?.failureCount ?: 0) + if (succeeded) 0 else 1,
            lastVerifiedAt = now
        )
        dao.upsert(entity)
        return toRecord(entity)
    }

    suspend fun saveManualKnowledge(
        packageName: String,
        appName: String,
        screenType: String,
        knownElements: List<String>,
        verifiedWorkflowHints: String
    ): Result<AppKnowledgeRecord> {
        val pkg = packageName.trim()
        if (pkg.length < 3 || !pkg.contains('.')) {
            return Result.failure(IllegalArgumentException("Valid Android package name required."))
        }
        if (AppBlocklistPolicy.isPackageBlocked(pkg)) {
            return Result.failure(SecurityException("Blocked saving knowledge for protected package '$pkg'."))
        }
        if (RoomLocalMemory.containsSensitiveInformation(verifiedWorkflowHints)) {
            return Result.failure(SecurityException("Sensitive credentials are never stored in Application Knowledge."))
        }
        val sig = "${screenType.trim().uppercase(Locale.ROOT).replace(' ', '_')}:manual"
        val existing = dao.findByPackageAndScreen(pkg, sig)
        val now = System.currentTimeMillis()
        val entity = AppKnowledgeEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            packageName = pkg,
            appName = appName.trim().ifBlank { inferFriendlyAppName(pkg) },
            screenSignature = sig,
            screenType = screenType.trim().ifBlank { "App Screen" },
            knownElementsJson = JSONArray(knownElements.distinct().take(18)).toString(),
            verifiedWorkflowHints = verifiedWorkflowHints.trim().take(480),
            successCount = (existing?.successCount ?: 0) + 1,
            failureCount = existing?.failureCount ?: 0,
            lastVerifiedAt = now
        )
        dao.upsert(entity)
        return Result.success(toRecord(entity))
    }

    suspend fun retrieveRelevantForPrompt(
        prompt: String,
        activePackage: String? = null,
        maxItems: Int = 4
    ): List<AppKnowledgeRecord> {
        val all = dao.getAllOnce().map(::toRecord)
        if (all.isEmpty()) return emptyList()
        val lower = prompt.lowercase(Locale.ROOT)
        return all
            .map { rec ->
                var score = 0
                if (!activePackage.isNullOrBlank() && rec.packageName.equals(activePackage, ignoreCase = true)) {
                    score += 50
                }
                if (lower.contains(rec.appName.lowercase(Locale.ROOT)) && rec.appName.length >= 3) {
                    score += 30
                }
                if (lower.contains(rec.packageName.lowercase(Locale.ROOT))) {
                    score += 30
                }
                score += (rec.successCount * 2 - rec.failureCount).coerceIn(0, 15)
                rec to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(maxItems.coerceIn(1, 8))
            .map { it.first }
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    fun formatForPrompt(records: List<AppKnowledgeRecord>): String {
        if (records.isEmpty()) return ""
        return buildString {
            appendLine("LEARNED APPLICATION KNOWLEDGE (facts observed from prior verified sessions):")
            records.forEach { rec ->
                append("- ${rec.appName} (${rec.packageName}) [${rec.screenType}, verified ${rec.successCount}x]: ")
                if (rec.knownElements.isNotEmpty()) {
                    append("Elements=${rec.knownElements.take(8).joinToString(", ")}. ")
                }
                if (rec.verifiedWorkflowHints.isNotBlank()) {
                    append("Hints: ${rec.verifiedWorkflowHints.take(180)}")
                }
                appendLine()
            }
        }.trim()
    }

    private fun toRecord(entity: AppKnowledgeEntity): AppKnowledgeRecord = AppKnowledgeRecord(
        id = entity.id,
        packageName = entity.packageName,
        appName = entity.appName,
        screenSignature = entity.screenSignature,
        screenType = entity.screenType,
        knownElements = parseStringList(entity.knownElementsJson),
        verifiedWorkflowHints = entity.verifiedWorkflowHints,
        successCount = entity.successCount,
        failureCount = entity.failureCount,
        lastVerifiedAt = entity.lastVerifiedAt
    )

    private fun parseStringList(raw: String): List<String> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun inferFriendlyAppName(packageName: String): String {
        val lower = packageName.lowercase(Locale.ROOT)
        return when {
            lower.contains("youtube") -> "YouTube"
            lower.contains("whatsapp") -> "WhatsApp"
            lower.contains("chrome") -> "Chrome"
            lower.contains("spotify") -> "Spotify"
            lower.contains("telegram") -> "Telegram"
            lower.contains("maps") -> "Maps"
            lower.contains("gmail") -> "Gmail"
            lower.contains("settings") -> "Settings"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
