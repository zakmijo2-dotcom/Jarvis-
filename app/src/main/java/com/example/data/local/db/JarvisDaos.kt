package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT c.id, c.title, c.createdAt, c.updatedAt, c.pinned,
               (SELECT m.content FROM messages m WHERE m.conversation_id = c.id ORDER BY m.timestamp DESC LIMIT 1) AS lastMessage,
               (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS messageCount
        FROM conversations c
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """
    )
    fun observeConversationsWithPreview(): Flow<List<ConversationWithPreview>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun countConversations(): Int

    @Query("SELECT id FROM conversations ORDER BY pinned DESC, updatedAt DESC LIMIT 1")
    suspend fun getMostRecentConversationId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM model_entries ORDER BY isActive DESC, providerType ASC, displayName ASC")
    fun observeAllModels(): Flow<List<ModelEntryEntity>>

    @Query("SELECT * FROM model_entries WHERE isActive = 1 LIMIT 1")
    fun observeActiveModel(): Flow<ModelEntryEntity?>

    @Query("SELECT * FROM model_entries WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModelOnce(): ModelEntryEntity?

    @Query("SELECT * FROM model_entries WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelEntryEntity?

    @Query("SELECT COUNT(*) FROM model_entries")
    suspend fun countModels(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<ModelEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: ModelEntryEntity)

    @Query("UPDATE model_entries SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE model_entries SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: String)

    @Transaction
    suspend fun setActiveModel(id: String) {
        clearActiveFlags()
        markActive(id)
    }

    @Query(
        """
        UPDATE model_entries
        SET localFilePath = :localFilePath,
            artifactState = :artifactState,
            status = :status
        WHERE id = :id
        """
    )
    suspend fun updateArtifactState(
        id: String,
        localFilePath: String?,
        artifactState: String,
        status: String
    )

    @Query("DELETE FROM model_entries WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomModel(id: String)

    @Query("DELETE FROM model_entries WHERE id = :id")
    suspend fun deleteModelById(id: String)
}

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_queue ORDER BY createdAt DESC")
    suspend fun getAllTasksOnce(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_queue WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_queue WHERE repoId = :repoId AND fileName = :fileName LIMIT 1")
    suspend fun findByRepoAndFile(repoId: String, fileName: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DownloadTaskEntity)

    @Query(
        """
        UPDATE download_queue
        SET downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speedBytesPerSec = :speedBytesPerSec,
            etaSeconds = :etaSeconds,
            status = :status,
            supportsResume = :supportsResume,
            errorMessage = :errorMessage,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long,
        status: String,
        supportsResume: Boolean,
        errorMessage: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM download_queue WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun clearFinished()
}

@Dao
interface ModelBenchmarkDao {
    @Query("SELECT * FROM model_benchmarks ORDER BY timestamp DESC LIMIT 100")
    fun observeBenchmarks(): Flow<List<ModelBenchmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenchmark(benchmark: ModelBenchmarkEntity)

    @Query("DELETE FROM model_benchmarks")
    suspend fun clearBenchmarks()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_notes ORDER BY importanceScore DESC, updatedAt DESC, createdAt DESC")
    fun observeFacts(): Flow<List<MemoryNoteEntity>>

    @Query("SELECT * FROM memory_notes ORDER BY importanceScore DESC, updatedAt DESC, createdAt DESC")
    suspend fun getAllFactsOnce(): List<MemoryNoteEntity>

    @Query("SELECT * FROM memory_notes WHERE content LIKE :pattern OR category LIKE :pattern ORDER BY importanceScore DESC, updatedAt DESC")
    suspend fun searchFacts(pattern: String): List<MemoryNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(note: MemoryNoteEntity)

    @Query("UPDATE memory_notes SET category = :category, content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFact(id: String, category: String, content: String, updatedAt: Long)

    @Query(
        """
        UPDATE memory_notes
        SET accessCount = accessCount + 1,
            lastAccessedAt = :accessedAt,
            importanceScore = MIN(10, importanceScore + 1)
        WHERE id = :id
        """
    )
    suspend fun recordAccess(id: String, accessedAt: Long)

    @Query(
        """
        UPDATE memory_notes
        SET updatedAt = :updatedAt,
            importanceScore = MIN(10, importanceScore + 1)
        WHERE id = :id
        """
    )
    suspend fun reinforceDuplicate(id: String, updatedAt: Long)

    @Query("DELETE FROM memory_notes WHERE expiresAt IS NOT NULL AND expiresAt <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long): Int

    @Query("DELETE FROM memory_notes WHERE id = :id")
    suspend fun deleteFactById(id: String)

    @Query("DELETE FROM memory_notes")
    suspend fun clearFacts()
}

@Dao
interface AgentExecutionDao {
    @Query("SELECT * FROM agent_executions ORDER BY timestamp DESC LIMIT 50")
    fun observeExecutions(): Flow<List<AgentExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: AgentExecutionEntity)

    @Query("DELETE FROM agent_executions")
    suspend fun clearExecutions()
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM agent_skills ORDER BY usageCount DESC, updatedAt DESC")
    fun observeSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM agent_skills ORDER BY usageCount DESC, updatedAt DESC")
    suspend fun getAllSkillsOnce(): List<SkillEntity>

    @Query("SELECT * FROM agent_skills WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(skill: SkillEntity)

    @Query("UPDATE agent_skills SET usageCount = usageCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementUsage(id: String, updatedAt: Long)

    @Query("DELETE FROM agent_skills WHERE id = :id")
    suspend fun deleteSkillById(id: String)

    @Query("DELETE FROM agent_skills")
    suspend fun clearSkills()
}

@Dao
interface AgentCheckpointDao {
    @Query("SELECT * FROM agent_checkpoints ORDER BY updatedAt DESC LIMIT 30")
    fun observeCheckpoints(): Flow<List<AgentCheckpointEntity>>

    @Query("SELECT * FROM agent_checkpoints ORDER BY updatedAt DESC LIMIT 30")
    suspend fun getRecentOnce(): List<AgentCheckpointEntity>

    @Query("SELECT * FROM agent_checkpoints WHERE conversationId = :conversationId AND status IN ('RUNNING', 'PAUSED_FOR_CONFIRMATION') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestResumableForConversation(conversationId: String): AgentCheckpointEntity?

    @Query("SELECT * FROM agent_checkpoints WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpoint(checkpoint: AgentCheckpointEntity)

    @Query("DELETE FROM agent_checkpoints WHERE id = :id")
    suspend fun deleteCheckpoint(id: String)

    @Query("DELETE FROM agent_checkpoints")
    suspend fun clearCheckpoints()
}

@Dao
interface ResearchHistoryDao {
    @Query("SELECT * FROM research_history ORDER BY timestamp DESC LIMIT 50")
    fun observeHistory(): Flow<List<ResearchHistoryEntity>>

    @Query("SELECT * FROM research_history ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentOnce(): List<ResearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ResearchHistoryEntity)

    @Query("DELETE FROM research_history")
    suspend fun clearHistory()
}

@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcp_servers ORDER BY enabled DESC, name ASC")
    fun observeServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers ORDER BY enabled DESC, name ASC")
    suspend fun getAllOnce(): List<McpServerEntity>

    @Query("SELECT * FROM mcp_servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): McpServerEntity?

    @Query("SELECT * FROM mcp_servers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): McpServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServer(server: McpServerEntity)

    @Query("UPDATE mcp_servers SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AppKnowledgeDao {
    @Query("SELECT * FROM app_knowledge ORDER BY successCount DESC, lastVerifiedAt DESC LIMIT 100")
    fun observeAll(): Flow<List<AppKnowledgeEntity>>

    @Query("SELECT * FROM app_knowledge ORDER BY successCount DESC, lastVerifiedAt DESC LIMIT 100")
    suspend fun getAllOnce(): List<AppKnowledgeEntity>

    @Query("SELECT * FROM app_knowledge WHERE LOWER(packageName) = LOWER(:packageName) ORDER BY successCount DESC, lastVerifiedAt DESC")
    suspend fun getByPackage(packageName: String): List<AppKnowledgeEntity>

    @Query("SELECT * FROM app_knowledge WHERE LOWER(packageName) = LOWER(:packageName) AND screenSignature = :screenSignature LIMIT 1")
    suspend fun findByPackageAndScreen(packageName: String, screenSignature: String): AppKnowledgeEntity?

    @Query("SELECT * FROM app_knowledge WHERE LOWER(appName) LIKE LOWER(:pattern) OR LOWER(packageName) LIKE LOWER(:pattern) OR LOWER(verifiedWorkflowHints) LIKE LOWER(:pattern) ORDER BY successCount DESC, lastVerifiedAt DESC LIMIT 20")
    suspend fun search(pattern: String): List<AppKnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AppKnowledgeEntity)

    @Query("DELETE FROM app_knowledge WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM app_knowledge")
    suspend fun clearAll()
}


