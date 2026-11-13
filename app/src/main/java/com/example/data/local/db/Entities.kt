package com.example.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM_NOTICE,
    TOOL_SNAPSHOT
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversation_id", "timestamp"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: MessageRole,
    val content: String,
    val modelLabel: String,
    val timestamp: Long,
    val ttftMs: Long? = null,
    val tokensPerSecond: Float? = null,
    val outputTokens: Int? = null,
    val backendUsed: String? = null
)

@Entity(tableName = "model_entries")
data class ModelEntryEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val providerType: String,
    val engineFamily: String,
    val parameterHint: String,
    val contextWindow: Int,
    val status: String,
    val isActive: Boolean,
    val isCustom: Boolean = false,
    val localFilePath: String? = null,
    val downloadUrl: String? = null,
    val expectedSha256: String? = null,
    val expectedSizeBytes: Long = 0L,
    val artifactState: String = "NOT_INSTALLED",
    val sourceRepoId: String? = null,
    val license: String? = null,
    val architecture: String? = null,
    val quantization: String? = null,
    val parameterCount: String? = null,
    val runtimeFormat: String = "LITERT_LM"
)

@Entity(tableName = "download_queue")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val repoId: String,
    val fileName: String,
    val displayName: String,
    val downloadUrl: String,
    val destinationPath: String,
    val expectedSha256: String? = null,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val status: String,
    val supportsResume: Boolean = false,
    val errorMessage: String? = null,
    val modelSlotId: String,
    val runtimeFormat: String,
    val architecture: String? = null,
    val quantization: String? = null,
    val parameterCount: String? = null,
    val license: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "model_benchmarks",
    indices = [Index(value = ["modelId", "timestamp"])]
)
data class ModelBenchmarkEntity(
    @PrimaryKey val id: String,
    val modelId: String,
    val modelName: String,
    val backendUsed: String,
    val ttftMs: Long,
    val tokensPerSecond: Float,
    val outputTokens: Int,
    val prefillTokens: Int,
    val nativeHeapMb: Float,
    val timestamp: Long
)

@Entity(
    tableName = "memory_notes",
    indices = [Index(value = ["kind", "createdAt"])]
)
data class MemoryNoteEntity(
    @PrimaryKey val id: String,
    val category: String,
    val content: String,
    val createdAt: Long,
    val kind: String = "FACT",
    val sourceConversationId: String? = null,
    val isUserApproved: Boolean = true,
    val updatedAt: Long = createdAt
)

@Entity(
    tableName = "agent_executions",
    indices = [Index(value = ["timestamp"])]
)
data class AgentExecutionEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val userPrompt: String,
    val routeTaken: String,
    val routeReason: String,
    val status: String,
    val stepsJson: String,
    val resultSummary: String,
    val durationMs: Long,
    val timestamp: Long
)

data class ConversationWithPreview(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val lastMessage: String?,
    val messageCount: Int
)
