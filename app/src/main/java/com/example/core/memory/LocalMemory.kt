package com.example.core.memory

import com.example.data.local.db.MemoryDao
import com.example.data.local.db.MemoryNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class MemoryFact(
    val id: String,
    val category: String,
    val content: String,
    val createdAt: Long
)

interface LocalMemory {
    fun observeFacts(): Flow<List<MemoryFact>>
    suspend fun saveFact(category: String, content: String)
    suspend fun clearFacts()
}

class RoomLocalMemory(
    private val memoryDao: MemoryDao
) : LocalMemory {
    override fun observeFacts(): Flow<List<MemoryFact>> =
        memoryDao.observeFacts().map { list ->
            list.map { MemoryFact(it.id, it.category, it.content, it.createdAt) }
        }

    override suspend fun saveFact(category: String, content: String) {
        val clean = content.trim()
        if (clean.isEmpty()) return
        memoryDao.insertFact(
            MemoryNoteEntity(
                id = UUID.randomUUID().toString(),
                category = category.trim().ifBlank { "General" },
                content = clean,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearFacts() {
        memoryDao.clearFacts()
    }
}
