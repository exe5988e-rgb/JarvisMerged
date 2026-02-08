//===== FILE: modules/core/src/main/kotlin/com/jarvismini/core/progress/ProgressEngine.kt =====
package com.jarvismini.core.progress

import android.content.Context

object ProgressEngine {

    fun markComplete(context: Context, blockId: String) {
        ProgressRepository.markComplete(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String) {
        ProgressRepository.markIncomplete(context, blockId)
    }
}
