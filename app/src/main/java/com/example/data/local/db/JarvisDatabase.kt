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
        MemoryNoteEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(JarvisTypeConverters::class)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun modelDao(): ModelDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun modelBenchmarkDao(): ModelBenchmarkDao
    abstract fun memoryDao(): MemoryDao

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

        fun create(context: Context): JarvisDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                JarvisDatabase::class.java,
                "jarvis_foundation.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
