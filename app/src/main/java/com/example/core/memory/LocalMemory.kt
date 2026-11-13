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
    PREFERENCE("Preference"),
    SUMMARY("Conversation Summary"),
    FACT("Fact"),
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
    val updatedAt: Long = createdAt
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

    suspend fun updateMemory(
        id: String,
        category: String,
        content: String,
        containsSensitiveConsent: Boolean = false
    ): Result<Unit>

    suspend fun deleteMemory(id: String)
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
        memoryDao.observeFacts().map { list -> list.map(::toModel) }

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
        val entity = MemoryNoteEntity(
            id = UUID.randomUUID().toString(),
            category = category.trim().ifBlank { kind.label },
            content = clean.take(600),
            createdAt = now,
            kind = kind.name,
            sourceConversationId = sourceConversationId,
            isUserApproved = userApproved,
            updatedAt = now
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

    override suspend fun searchMemories(query: String): List<MemoryFact> {
        val clean = query.trim()
        if (clean.isEmpty()) return memoryDao.getAllFactsOnce().map(::toModel)
        return memoryDao.searchFacts("%$clean%").map(::toModel)
    }

    override suspend fun retrieveRelevantContext(prompt: String, maxItems: Int): List<MemoryFact> {
        val all = memoryDao.getAllFactsOnce().filter { it.isUserApproved }.map(::toModel)
        if (all.isEmpty()) return emptyList()

        val tokens = prompt.lowercase(Locale.getDefault())
            .split(Regex("[^a-z0-9\\u0600-\\u06FF]+"))
            .filter { it.length >= 3 }
            .toSet()

        return all.sortedByDescending { item ->
            val text = "${item.category} ${item.content}".lowercase(Locale.getDefault())
            val overlap = tokens.count { token -> text.contains(token) }
            val prefBoost = if (item.kind == MemoryKind.PREFERENCE) 2 else 0
            (overlap * 10) + prefBoost
        }.take(maxItems.coerceIn(1, 10))
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

    private fun toModel(entity: MemoryNoteEntity): MemoryFact = MemoryFact(
        id = entity.id,
        category = entity.category,
        content = entity.content,
        createdAt = entity.createdAt,
        kind = MemoryKind.fromRaw(entity.kind),
        sourceConversationId = entity.sourceConversationId,
        isUserApproved = entity.isUserApproved,
        updatedAt = entity.updatedAt
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
