package com.jarvismini.core.progress

import android.content.Context

object ProgressStore {

    private val completedBlocks = mutableSetOf<String>()

    fun markComplete(context: Context, blockId: String) {
        completedBlocks.add(blockId)
    }

    fun markIncomplete(context: Context, blockId: String) {
        completedBlocks.remove(blockId)
    }

    fun getTodayBlocks(context: Context): List<ProgressBlock> {
        return completedBlocks.map { id ->
            ProgressBlock(
                id = id,
                completed = true
            )
        }
 
}

}
