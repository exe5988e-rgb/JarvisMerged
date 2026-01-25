package com.jarvismini.core.progress

object ProgressRepository {

    fun getTodayBlocks(): List<ProgressBlock> {
        return ProgressState.entries.map { entry ->
            ProgressBlock(
                id = entry.blockId,
                name = entry.name,
                completed = entry.completed
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
