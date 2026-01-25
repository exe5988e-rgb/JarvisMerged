package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.routine.model.Routine
import com.jarvismini.core.routine.model.RoutineAction
import org.json.JSONArray
import org.json.JSONObject

object RoutineProvider {

    private const val ROUTINES_DIR = "routines"

    fun getAllRoutines(context: Context): List<Routine> {
        val routines = mutableListOf<Routine>()
        val files = context.assets.list(ROUTINES_DIR) ?: return emptyList()

        files.filter { it.endsWith(".json") }.forEach { filename ->
            try {
                val json = context.assets.open("$ROUTINES_DIR/$filename")
                    .bufferedReader().use { it.readText() }

                val root = JSONObject(json)
                if (!root.has("routines")) return@forEach

                val array = root.getJSONArray("routines")
                routines += parseRoutines(array)
            } catch (_: Exception) {
                // ignore corrupted JSON files
            }
        }

        return routines.filter { it.enabled }
    }

    private fun parseRoutines(array: JSONArray): List<Routine> {
        val list = mutableListOf<Routine>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val enabled = obj.optBoolean("enabled", true)

            val actionsJson = obj.optJSONArray("actions") ?: JSONArray()
            val actions = mutableListOf<RoutineAction>()

            for (j in 0 until actionsJson.length()) {
                val a = actionsJson.getJSONObject(j)
                actions += RoutineAction(type = a.getString("type"), params = a.toMap())
            }

            list += Routine(id = id, name = name, enabled = enabled, actions = actions)
        }

        return list
    }
}

private fun JSONObject.toMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    keys().forEach { key ->
        map[key] = get(key).toString()
    }
    return map
}
