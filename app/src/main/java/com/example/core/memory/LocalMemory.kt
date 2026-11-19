package com.example.core.memory

import com.example.data.local.db.MemoryDao
import com.example.data.local.db.MemoryNoteEntity
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

enum class MemoryKind(val label: String) {
    PREFERENCE("User Preference"),
    APP_KNOWLEDGE("Application Knowledge"),
    TASK_HISTORY("Task History"),
    REUSABLE_SKILL("Reusable Skill"),
    TEMP_CONTEXT("Temporary Execution Context"),
    SUMMARY("Conversation Summary"),
    FACT("Fact"),
    PROJECT("Project Context"),
    CONTEXT("Historical Context");

    companion object {
        fun fromRaw(raw: String?): MemoryKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FACT
    }
}

data class MemoryFact(
    val id: String,
    val category: String,
    val content: String,
    val createdAt: Long,
    val kind: MemoryKind = MemoryKind.FACT,
    val sourceConversationId: String? = null,
    val isUserApproved: Boolean = true,
    val updatedAt: Long = createdAt,
    val importanceScore: Int = 1,
    val accessCount: Int = 0,
    val lastAccessedAt: Long = 0L,
    val expiresAt: Long? = null
)

interface LocalMemory {
    fun observeFacts(): Flow<List<MemoryFact>>
    suspend fun saveFact(category: String, content: String)
    suspend fun saveMemory(
        kind: MemoryKind,
        category: String,
        content: String,
        sourceConversationId: String? = null,
        userApproved: Boolean = true,
        containsSensitiveConsent: Boolean = false
    ): Result<MemoryFact>

    suspend fun saveMemoryWithLifecycle(
        kind: MemoryKind,
        category: String,
        content: String,
        sourceConversationId: String? = null,
        userApproved: Boolean = true,
        containsSensitiveConsent: Boolean = false,
        importanceScore: Int = 1,
        ttlMillis: Long? = null
    ): Result<MemoryFact> = saveMemory(
        kind = kind,
        category = category,
        content = content,
        sourceConversationId = sourceConversationId,
        userApproved = userApproved,
        containsSensitiveConsent = containsSensitiveConsent
    )

    suspend fun updateMemory(
        id: String,
        category: String,
        content: String,
        containsSensitiveConsent: Boolean = false
    ): Result<Unit>

    suspend fun deleteMemory(id: String)
    suspend fun purgeExpiredMemories(): Int = 0
    suspend fun searchMemories(query: String): List<MemoryFact>
    suspend fun retrieveRelevantContext(prompt: String, maxItems: Int = 5): List<MemoryFact>
    suspend fun summarizeAndStoreConversation(
        conversationId: String,
        title: String,
        messages: List<MessageEntity>,
        userApproved: Boolean = true
    ): MemoryFact?

    suspend fun clearFacts()
}

class RoomLocalMemory(
    private val memoryDao: MemoryDao
) : LocalMemory {

    override fun observeFacts(): Flow<List<MemoryFact>> =
        memoryDao.observeFacts().map { list ->
            val now = System.currentTimeMillis()
            list.filter { it.expiresAt == null || it.expiresAt > now }.map(::toModel)
        }

    override suspend fun saveFact(category: String, content: String) {
        saveMemory(
            kind = MemoryKind.FACT,
            category = category,
            content = content,
            userApproved = true,
            containsSensitiveConsent = false
        )
    }

    override suspend fun saveMemory(
        kind: MemoryKind,
        category: String,
        content: String,
        sourceConversationId: String?,
        userApproved: Boolean,
        containsSensitiveConsent: Boolean
    ): Result<MemoryFact> = saveMemoryWithLifecycle(
        kind = kind,
        category = category,
        content = content,
        sourceConversationId = sourceConversationId,
        userApproved = userApproved,
        containsSensitiveConsent = containsSensitiveConsent,
        importanceScore = if (kind == MemoryKind.PREFERENCE || kind == MemoryKind.PROJECT) 3 else 1,
        ttlMillis = null
    )

    override suspend fun saveMemoryWithLifecycle(
        kind: MemoryKind,
        category: String,
        content: String,
        sourceConversationId: String?,
        userApproved: Boolean,
        containsSensitiveConsent: Boolean,
        importanceScore: Int,
        ttlMillis: Long?
    ): Result<MemoryFact> {
        val clean = content.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Memory content cannot be empty."))
        }
        if (!containsSensitiveConsent && containsSensitiveInformation(clean)) {
            return Result.failure(
                SecurityException("Blocked storing sensitive information (API key, password, token, or financial/ID digits) without explicit consent.")
            )
        }
        val now = System.currentTimeMillis()
        memoryDao.deleteExpired(now)

        // Duplicate detection: reinforce existing equivalent memory rather than duplicating
        val normalizedNew = normalizeForDuplicate(clean)
        val existing = memoryDao.getAllFactsOnce()
            .firstOrNull { row ->
                row.kind == kind.name &&
                    normalizeForDuplicate(row.content) == normalizedNew
            }
        if (existing != null) {
            memoryDao.reinforceDuplicate(existing.id, now)
            return Result.success(
                toModel(
                    existing.copy(
                        updatedAt = now,
                        importanceScore = (existing.importanceScore + 1).coerceAtMost(10)
                    )
                )
            )
        }

        val entity = MemoryNoteEntity(
            id = UUID.randomUUID().toString(),
            category = category.trim().ifBlank { kind.label },
            content = clean.take(600),
            createdAt = now,
            kind = kind.name,
            sourceConversationId = sourceConversationId,
            isUserApproved = userApproved,
            updatedAt = now,
            importanceScore = importanceScore.coerceIn(1, 10),
            accessCount = 0,
            lastAccessedAt = 0L,
            expiresAt = ttlMillis?.takeIf { it > 0L }?.let { now + it }
        )
        memoryDao.insertFact(entity)
        return Result.success(toModel(entity))
    }

    override suspend fun updateMemory(
        id: String,
        category: String,
        content: String,
        containsSensitiveConsent: Boolean
    ): Result<Unit> {
        val clean = content.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Updated memory content cannot be empty."))
        }
        if (!containsSensitiveConsent && containsSensitiveInformation(clean)) {
            return Result.failure(
                SecurityException("Blocked sensitive information in memory edit without explicit consent.")
            )
        }
        memoryDao.updateFact(
            id = id,
            category = category.trim().ifBlank { "General" },
            content = clean.take(600),
            updatedAt = System.currentTimeMillis()
        )
        return Result.success(Unit)
    }

    override suspend fun deleteMemory(id: String) {
        memoryDao.deleteFactById(id)
    }

    override suspend fun purgeExpiredMemories(): Int =
        memoryDao.deleteExpired(System.currentTimeMillis())

    override suspend fun searchMemories(query: String): List<MemoryFact> {
        val now = System.currentTimeMillis()
        memoryDao.deleteExpired(now)
        val clean = query.trim()
        val rows = if (clean.isEmpty()) memoryDao.getAllFactsOnce() else memoryDao.searchFacts("%$clean%")
        return rows.filter { it.expiresAt == null || it.expiresAt > now }.map(::toModel)
    }

    override suspend fun retrieveRelevantContext(prompt: String, maxItems: Int): List<MemoryFact> {
        val now = System.currentTimeMillis()
        memoryDao.deleteExpired(now)
        val all = memoryDao.getAllFactsOnce()
            .filter { it.isUserApproved && (it.expiresAt == null || it.expiresAt > now) }
            .map(::toModel)
        if (all.isEmpty()) return emptyList()

        val tokens = prompt.lowercase(Locale.getDefault())
            .split(Regex("[^a-z0-9\\u0600-\\u06FF]+"))
            .filter { it.length >= 3 }
            .toSet()

        val ranked = all.sortedByDescending { item ->
            val text = "${item.category} ${item.content}".lowercase(Locale.getDefault())
            val overlap = tokens.count { token -> text.contains(token) }
            val kindBoost = when (item.kind) {
                MemoryKind.PREFERENCE -> 4
                MemoryKind.PROJECT -> 3
                else -> 0
            }
            val recencyBoost = if (now - item.updatedAt <= 24L * 3_600_000L) 2 else 0
            (overlap * 10) + (item.importanceScore * 2) + kindBoost + recencyBoost
        }.take(maxItems.coerceIn(1, 10))

        ranked.forEach { hit ->
            runCatching { memoryDao.recordAccess(hit.id, now) }
        }
        return ranked
    }

    override suspend fun summarizeAndStoreConversation(
        conversationId: String,
        title: String,
        messages: List<MessageEntity>,
        userApproved: Boolean
    ): MemoryFact? {
        if (!userApproved) return null
        val relevant = messages.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        if (relevant.isEmpty()) return null

        val userTopics = relevant
            .filter { it.role == MessageRole.USER }
            .takeLast(3)
            .joinToString(" | ") { it.content.replace("\n", " ").trim().take(75) }

        val lastReply = relevant
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.content
            ?.replace("\n", " ")
            ?.trim()
            ?.take(120)
            .orEmpty()

        val rawSummary = buildString {
            append("Session \"$title\": ")
            append("Topics: [$userTopics]")
            if (lastReply.isNotEmpty()) {
                append(" -> Outcome: $lastReply")
            }
        }

        if (containsSensitiveInformation(rawSummary)) {
            return null
        }

        return saveMemory(
            kind = MemoryKind.SUMMARY,
            category = "Session Summary",
            content = rawSummary,
            sourceConversationId = conversationId,
            userApproved = true,
            containsSensitiveConsent = false
        ).getOrNull()
    }

    override suspend fun clearFacts() {
        memoryDao.clearFacts()
    }

    private fun normalizeForDuplicate(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[.!?؟،,;:\"'`]+"), "")
            .trim()

    private fun toModel(entity: MemoryNoteEntity): MemoryFact = MemoryFact(
        id = entity.id,
        category = entity.category,
        content = entity.content,
        createdAt = entity.createdAt,
        kind = MemoryKind.fromRaw(entity.kind),
        sourceConversationId = entity.sourceConversationId,
        isUserApproved = entity.isUserApproved,
        updatedAt = entity.updatedAt,
        importanceScore = entity.importanceScore,
        accessCount = entity.accessCount,
        lastAccessedAt = entity.lastAccessedAt,
        expiresAt = entity.expiresAt
    )

    companion object {
        private val SENSITIVE_PATTERNS = listOf(
            Regex("""\bsk-[A-Za-z0-9_-]{12,}\b"""),
            Regex("""\bhf_[A-Za-z0-9]{10,}\b"""),
            Regex("""\bAIza[0-9A-Za-z_-]{16,}\b"""),
            Regex("""\b(?:\d[ -]*?){13,19}\b"""), // Payment card / long account numbers
            Regex("""\b(?:password|passcode|pin|secret_key|private_key)\s*[:=]\s*\S+""", RegexOption.IGNORE_CASE)
        )

        fun containsSensitiveInformation(text: String): Boolean =
            SENSITIVE_PATTERNS.any { it.containsMatchIn(text) }
    }
}
