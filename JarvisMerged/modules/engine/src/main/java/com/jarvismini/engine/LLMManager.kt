package com.jarvismini.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LLMManager {

    private var provider: LLMProvider? = null
    private var currentModel: String = "gpt-4o-mini"

    private val recommendedModels = listOf(
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-4.1",
        "gpt-5.1",
        "gpt-5.2",
        "o3",
        "gpt-image"
    )

    fun init(ctx: Context) {
        val prefs = ctx.getSharedPreferences("llm", Context.MODE_PRIVATE)
        currentModel = prefs.getString("chosen_model", "gpt-4o-mini")!!
        provider = PuterProvider(currentModel)
    }

    fun updateModel(ctx: Context, newModel: String) {
        currentModel = newModel
        provider = PuterProvider(newModel)

        ctx.getSharedPreferences("llm", Context.MODE_PRIVATE)
            .edit()
            .putString("chosen_model", newModel)
            .apply()
    }

    suspend fun generateReply(prompt: String): String =
        withContext(Dispatchers.IO) {
            (provider ?: PuterProvider(currentModel)).generateReply(prompt)
        }
}