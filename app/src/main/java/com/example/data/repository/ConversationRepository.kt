package com.example.data.repository

import com.example.data.local.db.ConversationDao
import com.example.data.local.db.ConversationEntity
import com.example.data.local.db.ConversationWithPreview
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationRepository(
    private val conversationDao: ConversationDao
) {
    fun observeConversations(): Flow<List<ConversationWithPreview>> =
        conversationDao.observeConversationsWithPreview()

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        conversationDao.observeMessages(conversationId)

    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity> =
        conversationDao.getMessagesOnce(conversationId)

    suspend fun ensureDefaultConversation(): String {
        val existing = conversationDao.getMostRecentConversationId()
        if (existing != null) return existing
        return createConversation(title = "Primary Core Session")
    }

    suspend fun createConversation(title: String = "New Core Session"): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        conversationDao.upsertConversation(
            ConversationEntity(
                id = id,
                title = title.trim().ifBlank { "New Core Session" },
                createdAt = now,
                updatedAt = now,
                pinned = false
            )
        )
        return id
    }

    suspend fun appendMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        modelLabel: String,
        ttftMs: Long? = null,
        tokensPerSecond: Float? = null,
        outputTokens: Int? = null,
        backendUsed: String? = null
    ) {
        val now = System.currentTimeMillis()
        conversationDao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = role,
                content = content,
                modelLabel = modelLabel,
                timestamp = now,
                ttftMs = ttftMs,
                tokensPerSecond = tokensPerSecond,
                outputTokens = outputTokens,
                backendUsed = backendUsed
            )
        )
        conversationDao.touchConversation(conversationId, now)
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            conversationDao.renameConversation(
                id = conversationId,
                title = trimmed,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun togglePinned(conversationId: String, currentlyPinned: Boolean) {
        conversationDao.setPinned(conversationId, !currentlyPinned)
    }

    suspend fun deleteConversation(conversationId: String): String {
        conversationDao.deleteConversation(conversationId)
        return ensureDefaultConversation()
    }

    suspend fun clearAllAndReset(): String {
        conversationDao.clearAllConversations()
        return createConversation(title = "Primary Core Session")
    }
}
