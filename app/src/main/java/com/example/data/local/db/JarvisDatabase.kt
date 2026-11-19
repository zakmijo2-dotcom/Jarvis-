package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class JarvisTypeConverters {
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String = role.name

    @TypeConverter
    fun toMessageRole(raw: String): MessageRole =
        runCatching { MessageRole.valueOf(raw) }.getOrDefault(MessageRole.SYSTEM_NOTICE)
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ModelEntryEntity::class,
        DownloadTaskEntity::class,
        ModelBenchmarkEntity::class,
        MemoryNoteEntity::class,
        AgentExecutionEntity::class,
        SkillEntity::class,
        AgentCheckpointEntity::class,
        ResearchHistoryEntity::class,
        McpServerEntity::class,
        AppKnowledgeEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(JarvisTypeConverters::class)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun modelDao(): ModelDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun modelBenchmarkDao(): ModelBenchmarkDao
    abstract fun memoryDao(): MemoryDao
    abstract fun agentExecutionDao(): AgentExecutionDao
    abstract fun skillDao(): SkillDao
    abstract fun agentCheckpointDao(): AgentCheckpointDao
    abstract fun researchHistoryDao(): ResearchHistoryDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun appKnowledgeDao(): AppKnowledgeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN ttftMs INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN tokensPerSecond REAL")
                db.execSQL("ALTER TABLE messages ADD COLUMN outputTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN backendUsed TEXT")

                db.execSQL("ALTER TABLE model_entries ADD COLUMN localFilePath TEXT")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN downloadUrl TEXT")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN expectedSha256 TEXT")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN expectedSizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN artifactState TEXT NOT NULL DEFAULT 'NOT_INSTALLED'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE model_entries ADD COLUMN sourceRepoId TEXT")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN license TEXT")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN architecture TEXT")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN quantization TEXT")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN parameterCount TEXT")
                db.execSQL("ALTER TABLE model_entries ADD COLUMN runtimeFormat TEXT NOT NULL DEFAULT 'LITERT_LM'")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS download_queue (
                        id TEXT NOT NULL PRIMARY KEY,
                        repoId TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        downloadUrl TEXT NOT NULL,
                        destinationPath TEXT NOT NULL,
                        expectedSha256 TEXT,
                        totalBytes INTEGER NOT NULL,
                        downloadedBytes INTEGER NOT NULL,
                        speedBytesPerSec INTEGER NOT NULL,
                        etaSeconds INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        supportsResume INTEGER NOT NULL,
                        errorMessage TEXT,
                        modelSlotId TEXT NOT NULL,
                        runtimeFormat TEXT NOT NULL,
                        architecture TEXT,
                        quantization TEXT,
                        parameterCount TEXT,
                        license TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS model_benchmarks (
                        id TEXT NOT NULL PRIMARY KEY,
                        modelId TEXT NOT NULL,
                        modelName TEXT NOT NULL,
                        backendUsed TEXT NOT NULL,
                        ttftMs INTEGER NOT NULL,
                        tokensPerSecond REAL NOT NULL,
                        outputTokens INTEGER NOT NULL,
                        prefillTokens INTEGER NOT NULL,
                        nativeHeapMb REAL NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_model_benchmarks_modelId_timestamp ON model_benchmarks (modelId, timestamp)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_notes ADD COLUMN kind TEXT NOT NULL DEFAULT 'FACT'")
                db.execSQL("ALTER TABLE memory_notes ADD COLUMN sourceConversationId TEXT")
                db.execSQL("ALTER TABLE memory_notes ADD COLUMN isUserApproved INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE memory_notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE memory_notes SET updatedAt = createdAt WHERE updatedAt = 0")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_notes_kind_createdAt ON memory_notes (kind, createdAt)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_executions (
                        id TEXT NOT NULL PRIMARY KEY,
                        conversationId TEXT NOT NULL,
                        userPrompt TEXT NOT NULL,
                        routeTaken TEXT NOT NULL,
                        routeReason TEXT NOT NULL,
                        status TEXT NOT NULL,
                        stepsJson TEXT NOT NULL,
                        resultSummary TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_agent_executions_timestamp ON agent_executions (timestamp)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE model_benchmarks ADD COLUMN initTimeMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE model_benchmarks ADD COLUMN peakMemoryMb REAL NOT NULL DEFAULT 0")
                db.execSQL("UPDATE model_benchmarks SET peakMemoryMb = nativeHeapMb WHERE peakMemoryMb = 0")
                db.execSQL("ALTER TABLE model_benchmarks ADD COLUMN modelSizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE model_benchmarks ADD COLUMN thermalStatus TEXT NOT NULL DEFAULT 'Nominal'")
                db.execSQL("ALTER TABLE model_benchmarks ADD COLUMN batteryPercent INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE model_benchmarks ADD COLUMN batteryTempCelsius REAL NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_queue ADD COLUMN contextWindow INTEGER NOT NULL DEFAULT 4096")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_notes ADD COLUMN importanceScore INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE memory_notes ADD COLUMN accessCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memory_notes ADD COLUMN lastAccessedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memory_notes ADD COLUMN expiresAt INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_skills (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        triggerPattern TEXT NOT NULL,
                        requiredPermissionsJson TEXT NOT NULL,
                        parametersJson TEXT NOT NULL,
                        stepsJson TEXT NOT NULL,
                        validationRules TEXT NOT NULL,
                        failureHandling TEXT NOT NULL,
                        version INTEGER NOT NULL DEFAULT 1,
                        provenance TEXT NOT NULL,
                        riskLevel TEXT NOT NULL,
                        usageCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_agent_skills_name ON agent_skills (name)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS agent_checkpoints (
                        id TEXT NOT NULL PRIMARY KEY,
                        conversationId TEXT NOT NULL,
                        userGoal TEXT NOT NULL,
                        currentStep INTEGER NOT NULL,
                        maxSteps INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        lastPackageName TEXT,
                        lastStabilityHash TEXT,
                        stepsHistoryJson TEXT NOT NULL,
                        lastObservationSummary TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_agent_checkpoints_conversationId_updatedAt ON agent_checkpoints (conversationId, updatedAt)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS research_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        query TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        urlsJson TEXT NOT NULL,
                        summarySnippet TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_research_history_timestamp ON research_history (timestamp)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mcp_servers (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        endpointUrl TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 0,
                        requireWriteConfirmation INTEGER NOT NULL DEFAULT 1,
                        sessionId TEXT,
                        cachedToolsJson TEXT NOT NULL DEFAULT '[]',
                        lastStatus TEXT NOT NULL DEFAULT 'Configured',
                        lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_mcp_servers_name ON mcp_servers (name)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agent_skills ADD COLUMN preconditions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agent_skills ADD COLUMN expectedOutcomes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agent_skills ADD COLUMN verificationConditions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agent_skills ADD COLUMN targetPackage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agent_skills ADD COLUMN minAndroidSdk INTEGER NOT NULL DEFAULT 24")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_knowledge (
                        id TEXT NOT NULL PRIMARY KEY,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        screenSignature TEXT NOT NULL,
                        screenType TEXT NOT NULL,
                        knownElementsJson TEXT NOT NULL,
                        verifiedWorkflowHints TEXT NOT NULL,
                        successCount INTEGER NOT NULL,
                        failureCount INTEGER NOT NULL,
                        lastVerifiedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_app_knowledge_packageName_screenSignature ON app_knowledge (packageName, screenSignature)"
                )
            }
        }

        fun create(context: Context): JarvisDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                JarvisDatabase::class.java,
                "jarvis_foundation.db"
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9
                )
                .build()
    }
}
