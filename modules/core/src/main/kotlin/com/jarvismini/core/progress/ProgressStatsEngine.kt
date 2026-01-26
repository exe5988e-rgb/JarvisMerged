package com.jarvismini.core.progress

import java.text.SimpleDateFormat
import java.util.*

object ProgressStatsEngine {

    fun getTodayStats(): ProgressStats {
        val blocks = ProgressStore.getTodayBlocks()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        return ProgressStats(
            date = today,
            totalBlocks = blocks.size,
            completedBlocks = blocks.count { it.completed }
        )
    }
}


---

✅ 6️⃣ ProgressInitializer (DAILY SAFE REGISTRATION)
