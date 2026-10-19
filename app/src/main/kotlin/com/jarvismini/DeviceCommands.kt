package com.jarvismini

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.MediaController
import com.jarvismini.core.ModeCommands

/**
 * On-device voice/chat commands for spec sections 6 (media), 7 (device control),
 * 13 (switch LLM by voice) and sleep/wake. Works offline. Returns the reply to speak,
 * or null when the text is not one of these commands.
 *
 * Android does not let a normal app flip Wi-Fi, Bluetooth, hotspot or battery saver
 * directly, so for those Jarvis opens the right settings screen and says so.
 */
object DeviceCommands {

    private lateinit var app: Context
    private val OFF = setOf("off", "disable", "deactivate", "stop")

    private val MODELS = linkedMapOf(
        "deepseek" to "deepseek/deepseek-v4.1-flash-1017",
        "haiku" to "anthropic/claude-haiku-5.5",
        "claude" to "anthropic/claude-haiku-5.5",
        "nemotron" to "nvidia/nemotron-3-ultra-550b-a55b:free",
        "nvidia" to "nvidia/nemotron-3-ultra-550b-a55b:free",
        "gemini" to "google/gemini-4-argon",
        "gpt" to "openai/gpt-6.1-sol-pro",
        "openai" to "openai/gpt-6.1-sol-pro"
    )

    fun install(context: Context) {
        app = context.applicationContext
        ModeCommands.extra = { text -> try { handle(text) } catch (e: Exception) { null } }
    }

    private fun handle(t: String): String? {
        val words = t.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        val off = words.any { it in OFF }

        // ---- answer to something Jarvis just asked ----
        com.jarvismini.proactive.ProactiveEngine.answer(t)?.let { return it }

        // ---- LLM switch ----
        if (t.startsWith("switch to ") || t.startsWith("use the ") || t.startsWith("change model")) {
            val key = MODELS.keys.firstOrNull { t.contains(it) } ?: return "I do not know that model. Try deepseek, haiku, nemotron, gemini or gpt."
            return switchModel(key)
        }

        // ---- sleep / wake ----
        if (t.contains("good night") || t == "sleep mode" || t.contains("enable sleep mode") || t.contains("start sleep mode")) {
            JarvisState.setMode(app, JarvisMode.SLEEP)
            return "Sleep mode. I will only wake you for critical events."
        }
        if (t.contains("good morning") || t.contains("wake up") || t.contains("end sleep mode") || t.contains("exit sleep")) {
            if (JarvisState.currentMode == JarvisMode.SLEEP) {
                JarvisState.setMode(app, JarvisMode.NORMAL)
                return "Waking up." // the wake-up digest is spoken by the announcement engine
            }
            return null
        }

        // ---- media ----
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when {
            t.contains("volume") || t == "mute" || t == "unmute" -> {
                val pct = Regex("""(\d{1,3})""").find(t)?.value?.toIntOrNull()
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                return when {
                    t == "mute" || t.contains("mute") && !t.contains("unmute") -> { am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0); "Muted." }
                    pct != null -> { am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * pct.coerceIn(0, 100) / 100), AudioManager.FLAG_SHOW_UI); "Volume set to ${pct.coerceIn(0, 100)} percent." }
                    t.contains("up") || t.contains("increase") || t.contains("raise") || t.contains("louder") -> { MediaController.volumeUp(app); "Volume up." }
                    t.contains("down") || t.contains("decrease") || t.contains("lower") || t.contains("quieter") -> { MediaController.volumeDown(app); "Volume down." }
                    else -> null
                }
            }
            t == "next" || t.startsWith("next song") || t.startsWith("next track") || t.contains("skip song") || t.contains("skip track") || t.contains("skip this") -> { MediaController.next(app); return "Next." }
            t.startsWith("previous") || t.contains("go back a song") || t.contains("last song") -> { MediaController.previous(app); return "Previous." }
            t == "pause" || t == "play" || t == "resume" || t.startsWith("pause the") || t.startsWith("pause music") ||
                t.startsWith("play music") || t.startsWith("resume music") || t.startsWith("pause song") || t.startsWith("stop music") -> {
                MediaController.playPause(app); return "Done."
            }
        }

        // ---- device control ----
        if (t.contains("torch") || t.contains("flashlight")) {
            return setTorch(!off)
        }
        if (t.contains("rotation") || t.contains("auto rotate") || t.contains("auto-rotate")) {
            return setRotation(!off)
        }
        if (t.contains("wifi") || t.contains("wi-fi")) {
            openSettings(if (android.os.Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS)
            return "Android does not let apps switch Wi-Fi directly. I opened the panel for you."
        }
        if (t.contains("bluetooth")) {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            return "Android does not let apps switch Bluetooth directly. I opened the settings for you."
        }
        if (t.contains("hotspot") || t.contains("tethering")) {
            openSettings("android.settings.TETHER_SETTINGS")
            return "I opened the hotspot settings. Android blocks apps from toggling it directly."
        }
        if (t.contains("power saving") || t.contains("battery saver")) {
            openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            return "I opened battery saver settings."
        }

        // ---- open app (offline, by installed app name) ----
        if (t.startsWith("open ") || t.startsWith("launch ")) {
            val name = t.substringAfter(' ').trim().removePrefix("the ").removeSuffix(" app").trim()
            if (name.isNotEmpty()) return launchApp(name)
        }
        return null
    }

    private fun switchModel(key: String): String {
        val chosen = MODELS.getValue(key)
        val prefs = app.getSharedPreferences("jarvis_llm", Context.MODE_PRIVATE)
        val current = (prefs.getString("openrouter_models", "") ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rest = (current.ifEmpty { MODELS.values.distinct() }).filter { it != chosen }
        prefs.edit().putString("openrouter_models", (listOf(chosen) + rest).joinToString("\n")).apply()
        return "Switched to $key. The next replies use $chosen first."
    }

    private fun setTorch(on: Boolean): String {
        val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it)
            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "This phone has no torch."
        cm.setTorchMode(id, on)
        return if (on) "Torch on." else "Torch off."
    }

    private fun setRotation(on: Boolean): String {
        if (!Settings.System.canWrite(app)) {
            openSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:" + app.packageName)
            return "I need the modify system settings permission for that. Allow it and ask again."
        }
        Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        return if (on) "Auto rotate on." else "Auto rotate off."
    }

    private fun launchApp(name: String): String? {
        val pm = app.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hits = pm.queryIntentActivities(intent, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val match = hits.firstOrNull { it.first.lowercase() == name }
            ?: hits.firstOrNull { it.first.lowercase().startsWith(name) }
            ?: hits.firstOrNull { it.first.lowercase().contains(name) }
            ?: return null // not an installed app: let the agent handle it
        val launch = pm.getLaunchIntentForPackage(match.second) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(launch)
        return "Opening ${match.first}."
    }

    private fun openSettings(action: String, data: String? = null) {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (data != null) i.data = android.net.Uri.parse(data)
        app.startActivity(i)
    }
}
