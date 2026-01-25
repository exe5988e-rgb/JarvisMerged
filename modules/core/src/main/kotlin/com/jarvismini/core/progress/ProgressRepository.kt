package com.jarvismini.core.progress

object ProgressRepository {

    fun getTodayBlocks(): List<ProgressBlock> {
        // ✅ Get all registered blocks for today
        val registered = ProgressStore.getRegisteredBlocks(JarvisApp.context)
        val completed = ProgressStore.getCompletedBlocks(JarvisApp.context)

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
