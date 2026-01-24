package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.routine.model.Routine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provides access to all routines defined in assets.
 * Loads original routines (daily, weekly, etc.) from JSON files.
 */
object RoutineProvider {

    private const val ROUTINES_DIR = "routines"

    /**
     * Load all routines from assets as a List<Routine>.
     */
    fun getAllRoutines(context: Context): List<Routine> {
        val routines = mutableListOf<Routine>()
        try {
            val files = context.assets.list(ROUTINES_DIR) ?: arrayOf()
            files.forEach { filename ->
                if (filename.endsWith(".json")) {
                    val jsonStr = context.assets.open("$ROUTINES_DIR/$filename")
                        .bufferedReader().use { it.readText() }
                    val root = JSONObject(jsonStr)
                    val routinesArray = root.getJSONArray("routines")
                    routines.addAll(parseRoutines(routinesArray))
                }
            }
        } catch (_: Exception) {
            // Ignore or log
        }
        return routines
    }

    private fun parseRoutines(array: JSONArray): List<Routine> {
        val list = mutableListOf<Routine>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val enabled = obj.optBoolean("enabled", true)
            val actionsArray = obj.optJSONArray("actions") ?: JSONArray()
            val actions = mutableListOf<RoutineAction>()
            for (j in 0 until actionsArray.length()) {
                val a = actionsArray.getJSONObject(j)
                actions.add(
                    RoutineAction(
                        type = a.getString("type"),
                        params = a.toMap()
                    )
                )
            }
            list.add(Routine(id = id, name = name, enabled = enabled, actions = actions))
        }
        return list
    }
}

/**
 * Simple extension to convert JSONObject to Map<String, String>
 */
private fun JSONObject.toMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    keys().forEach { key ->
        map[key] = get(key).toString()
    }
    return map
}
