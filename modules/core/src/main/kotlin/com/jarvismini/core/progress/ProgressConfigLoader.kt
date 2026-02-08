package com.jarvismini.core.progress

import android.content.Context
import org.json.JSONObject

object ProgressConfigLoader {

fun load(context: Context): ProgressConfig {  
    val json = context.assets  
        .open("routines/progress_config.json")  
        .bufferedReader()  
        .use { it.readText() }  

    val root = JSONObject(json)  
    val p = root.getJSONObject("progressTracking")  

    val retry = p.getJSONObject("retryPolicy")  
    val tts = p.getJSONObject("tts")  
    val quiet = p.getJSONObject("quietHours")  

    return ProgressConfig(  
        enabled = p.getBoolean("enabled"),  
        retryEnabled = retry.getBoolean("retryOnIncomplete"),  
        retryDelayMs = retry.getLong("retryDelayMinutes") * 60_000,  
        ttsEnabled = tts.getBoolean("enabled"),  
        ttsRepeat = tts.getBoolean("repeatOnRetry"),  
        quietStart = quiet.getString("start"),  
        quietEnd = quiet.getString("end")  
    )  
}

}
