package com.jarvismini.core.progress

/**
 * Repository for managing daily progress blocks.
 * Provides context-free methods for UI layer to query and update block status.
 */
object ProgressRepository {

    fun getTodayBlocks(): List<ProgressBlock> {
        // ✅ Get all registered blocks for today
        val registered = ProgressStore.getRegisteredBlocks()
        val completed = ProgressStore.getCompletedBlocks()

        return registered.map { blockId ->
            ProgressBlock(
                id = blockId,
                name = blockId, // Optionally map to a proper display name
                completed = completed.contains(blockId)
            )
        }
    }

    fun markCompleted(blockId: String) {
        ProgressEngine.markComplete(blockId)
    }

    fun markIncomplete(blockId: String, blockName: String) {
        ProgressEngine.markIncomplete(blockId, blockName)
    }
}
