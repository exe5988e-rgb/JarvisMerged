package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.engine.progress.AlarmManagerRetry

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        // 1️⃣ Persist completion
        ProgressStore.markComplete(context, blockId)

        // 2️⃣ 🛑 CANCEL any scheduled reminder
        AlarmManagerRetry.cancel(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String, blockName: String) {
        ProgressStore.markIncomplete(context, blockId)
        // scheduling handled elsewhere
  
}
}
