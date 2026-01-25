package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.routine.model.Routine
import com.jarvismini.core.routine.model.Trigger
import org.json.JSONObject

object RoutineProvider {

    fun getAllRoutines(context: Context): List<Routine> {
        val routines = mutableListOf<Routine>()

        // list all JSON files in assets/routines/
        val files = context.assets.list("routines") ?: return emptyList()

        for (file in files) {
            val jsonStr = context.assets.open("routines/$file")
                .bufferedReader()
                .use { it.readText() }

            val r = JSONObject(jsonStr)

            // Parse trigger object
            val triggerObj = r.getJSONObject("trigger")
            val trigger = Trigger(
                type = triggerObj.getString("type"),
                time = triggerObj.getString("time"),
                days = triggerObj.getJSONArray("days").let { array ->
                    List(array.length()) { idx -> array.getString(idx) }
                }
            )

            // Add routine to list
            routines.add(
                Routine(
                    id = r.getString("id"),
                    name = r.getString("name"),
                    enabled = r.getBoolean("enabled"),
                    trigger = trigger
                )
            )
        }

        return routines
    }
}
