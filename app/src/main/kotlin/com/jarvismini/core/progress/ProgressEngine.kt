package com.jarvismini.core.progress

import android.content.Context

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        val blocks = ProgressRepository.getTodayBlocks().toMutableList()
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index == -1) return

        blocks[index] = blocks[index].copy(
            completed = true,
            startTimestamp = 0L
        )
        ProgressRepository.saveTodayBlocks(blocks)
    }

    fun markIncomplete(context: Context, blockId: String) {
        val blocks = ProgressRepository.getTodayBlocks().toMutableList()
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index == -1) return

        blocks[index] = blocks[index].copy(completed = false)
        ProgressRepository.saveTodayBlocks(blocks)
    }

    // NEW: start task with user duration
    fun startTask(context: Context, blockId: String, durationMinutes: Int) {
        val blocks = ProgressRepository.getTodayBlocks().toMutableList()
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index == -1) return

        blocks[index] = blocks[index].copy(
            durationMinutes = durationMinutes,
            startTimestamp = System.currentTimeMillis()
        )

        ProgressRepository.saveTodayBlocks(blocks)
    }
}
