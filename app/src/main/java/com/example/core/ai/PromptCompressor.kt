package com.example.core.ai

import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole

data class CompressedHistoryTurn(
    val isUser: Boolean,
    val content: String,
    val isCompactedDigest: Boolean = false
)

data class PromptCompressionResult(
    val turns: List<CompressedHistoryTurn>,
    val compactedTurnCount: Int,
    val estimatedHistoryTokens: Int
)

/**
 * Deterministic conversation history compressor and context-window manager.
 * Keeps the freshest conversational turns verbatim and summarizes older turns
 * into a tight factual digest so local models with 2K-8K context windows stay fast
 * without losing key constraints or overflowing KV cache.
 */
object PromptCompressor {

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return ((text.length + 3) / 4).coerceAtLeast(1)
    }

    fun compressHistory(
        history: List<MessageEntity>,
        contextBudgetTokens: Int,
        maxRecentTurns: Int = 8
    ): PromptCompressionResult {
        val conversational = history.filter {
            (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) &&
                it.content.isNotBlank()
        }
        if (conversational.isEmpty()) {
            return PromptCompressionResult(
                turns = emptyList(),
                compactedTurnCount = 0,
                estimatedHistoryTokens = 0
            )
        }

        // Reserve ~45% of total context for system prompt, tool schemas, current user turn, and output reply
        val totalHistoryBudget = (contextBudgetTokens * 0.55f).toInt().coerceAtLeast(256)
        val recentBudgetTokens = (totalHistoryBudget * 0.75f).toInt().coerceAtLeast(192)
        val summaryBudgetChars = ((totalHistoryBudget - recentBudgetTokens) * 3.6f).toInt().coerceIn(240, 1400)

        var usedRecentTokens = 0
        val selectedRecentReversed = mutableListOf<MessageEntity>()
        var cutoffIndex = conversational.size

        for (i in conversational.indices.reversed()) {
            val item = conversational[i]
            val cleanText = item.content.trim()
            val tok = estimateTokens(cleanText)
            if (selectedRecentReversed.size >= maxRecentTurns ||
                (usedRecentTokens + tok > recentBudgetTokens && selectedRecentReversed.isNotEmpty())
            ) {
                break
            }
            usedRecentTokens += tok
            selectedRecentReversed.add(item)
            cutoffIndex = i
        }

        val olderTurns = if (cutoffIndex > 0) conversational.subList(0, cutoffIndex) else emptyList()
        val finalTurns = mutableListOf<CompressedHistoryTurn>()

        if (olderTurns.isNotEmpty()) {
            val digestText = buildOlderTurnsDigest(olderTurns, maxChars = summaryBudgetChars)
            if (digestText.isNotBlank()) {
                finalTurns.add(
                    CompressedHistoryTurn(
                        isUser = true,
                        content = digestText,
                        isCompactedDigest = true
                    )
                )
            }
        }

        selectedRecentReversed.asReversed().forEach { msg ->
            finalTurns.add(
                CompressedHistoryTurn(
                    isUser = msg.role == MessageRole.USER,
                    content = msg.content.trim(),
                    isCompactedDigest = false
                )
            )
        }

        val totalEst = finalTurns.sumOf { estimateTokens(it.content) }
        return PromptCompressionResult(
            turns = finalTurns,
            compactedTurnCount = olderTurns.size,
            estimatedHistoryTokens = totalEst
        )
    }

    private fun buildOlderTurnsDigest(turns: List<MessageEntity>, maxChars: Int): String {
        val sb = StringBuilder()
        sb.appendLine("[COMPACTED PRIOR CONTEXT — ${turns.size} earlier turn(s)]")
        for (msg in turns) {
            val speaker = if (msg.role == MessageRole.USER) "User" else "Assistant"
            val snippet = msg.content
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(120)
            val line = "- $speaker: $snippet"
            if (sb.length + line.length + 1 > maxChars) break
            sb.appendLine(line)
        }
        return sb.toString().trim()
    }
}
