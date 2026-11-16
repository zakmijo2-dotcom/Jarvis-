package com.example.core.vision

data class ScreenJournalEntry(
    val stepNumber: Int,
    val stabilityHash: String,
    val packageName: String?,
    val actionTaken: String,
    val stateChanged: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Clean-room revisit & loop detector inspired by OpenPhoneUse ScreenJournal.
 * Detects screen stagnation and A->B->A->B oscillation during autonomous agent execution.
 */
class ScreenJournal(
    private val maxEntries: Int = 25
) {
    private val entries = ArrayDeque<ScreenJournalEntry>()

    @Synchronized
    fun record(
        stepNumber: Int,
        stabilityHash: String,
        packageName: String?,
        actionTaken: String,
        stateChanged: Boolean
    ) {
        if (entries.size >= maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(
            ScreenJournalEntry(
                stepNumber = stepNumber,
                stabilityHash = stabilityHash,
                packageName = packageName,
                actionTaken = actionTaken,
                stateChanged = stateChanged
            )
        )
    }

    @Synchronized
    fun recentEntries(limit: Int = 6): List<ScreenJournalEntry> =
        entries.toList().takeLast(limit)

    @Synchronized
    fun countHashVisits(stabilityHash: String): Int =
        entries.count { it.stabilityHash == stabilityHash }

    @Synchronized
    fun consecutiveNoChangeCount(): Int {
        var count = 0
        for (entry in entries.reversed()) {
            if (!entry.stateChanged) count++ else break
        }
        return count
    }

    @Synchronized
    fun isStalledOrOscillating(currentHash: String): Boolean {
        val list = entries.toList()
        if (consecutiveNoChangeCount() >= 2) return true
        if (countHashVisits(currentHash) >= 3) return true
        if (list.size >= 4) {
            val tail = list.takeLast(4).map { it.stabilityHash }
            if (tail[0] == tail[2] && tail[1] == tail[3] && tail[0] != tail[1]) {
                return true
            }
        }
        return false
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
