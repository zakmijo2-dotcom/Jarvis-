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
    val updatedAt: Long,
    val contextWindow: Int = 4096
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
    val timestamp: Long,
    val initTimeMs: Long = 0L,
    val peakMemoryMb: Float = nativeHeapMb,
    val modelSizeBytes: Long = 0L,
    val thermalStatus: String = "Nominal",
    val batteryPercent: Int = -1,
    val batteryTempCelsius: Float = 0f
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
    val updatedAt: Long = createdAt,
    val importanceScore: Int = 1,
    val accessCount: Int = 0,
    val lastAccessedAt: Long = 0L,
    val expiresAt: Long? = null
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

@Entity(
    tableName = "agent_skills",
    indices = [Index(value = ["name"], unique = true)]
)
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val triggerPattern: String,
    val requiredPermissionsJson: String,
    val parametersJson: String,
    val stepsJson: String,
    val validationRules: String,
    val failureHandling: String,
    val version: Int = 1,
    val provenance: String = "User Distilled",
    val riskLevel: String = "LOW",
    val usageCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "agent_checkpoints",
    indices = [Index(value = ["conversationId", "updatedAt"])]
)
data class AgentCheckpointEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val userGoal: String,
    val currentStep: Int,
    val maxSteps: Int,
    val status: String,
    val lastPackageName: String?,
    val lastStabilityHash: String?,
    val stepsHistoryJson: String,
    val lastObservationSummary: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "research_history",
    indices = [Index(value = ["timestamp"])]
)
data class ResearchHistoryEntity(
    @PrimaryKey val id: String,
    val query: String,
    val provider: String,
    val urlsJson: String,
    val summarySnippet: String,
    val timestamp: Long
)

@Entity(
    tableName = "mcp_servers",
    indices = [Index(value = ["name"], unique = true)]
)
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val endpointUrl: String,
    val enabled: Boolean = false,
    val requireWriteConfirmation: Boolean = true,
    val sessionId: String? = null,
    val cachedToolsJson: String = "[]",
    val lastStatus: String = "Configured",
    val lastSyncedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

