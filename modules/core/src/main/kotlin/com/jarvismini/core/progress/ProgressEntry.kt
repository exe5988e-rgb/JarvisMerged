package com.jarvismini.core.progress

data class ProgressEntry(
    val routineId: String,
    val blockId: String,
    val timestamp: Long, // creation day marker
    val state: ProgressState = ProgressState.PENDING,
    val completedAt: Long? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val missedAt: Long? = null
)

❌ scheduledAt REMOVED (was breaking day logic)


---

✅ 3️⃣ ProgressStore (CORE FIX)
