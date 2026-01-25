package com.jarvismini.core.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Utility for loading JSON and parsing to objects.
 */
object JsonUtil {

    fun loadJsonFromAssets(context: Context, assetPath: String): String? {
        return try {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    inline fun <reified T> fromJsonArray(json: String): List<T> {
        val type = object : TypeToken<List<T>>() {}.type
        return Gson().fromJson(json, type)
    }
}
