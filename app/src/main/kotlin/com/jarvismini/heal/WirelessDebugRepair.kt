package com.jarvismini.heal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.jarvismini.core.JarvisPrefs

/**
 * WirelessDebugRepair: turns Wireless debugging back on when Android switches it off (it does so
 * on every Wi-Fi change, network switch and reboot), so the second ADB channel to this device
 * stays reachable without a trip to Developer options.
 *
 * Writes Settings.Global "adb_wifi_enabled", which needs WRITE_SECURE_SETTINGS, granted once via:
 *
 *   adb shell pm grant com.jarvismini android.permission.WRITE_SECURE_SETTINGS
 *
 * Without the grant [ensureEnabled] is a no-op that returns false. Only ever switches the setting
 * on, and only while the user hasn't turned [PREF_ENABLED] off. Never throws.
 */
object WirelessDebugRepair {

    private const val TAG = "WifiAdbRepair"
    private const val SETTING = "adb_wifi_enabled"

    /** Set false to stop the watchdog re-enabling Wireless debugging. Defaults to on. */
    const val PREF_ENABLED = "heal_wireless_debug_auto"

    fun canRepair(context: Context): Boolean =
        context.applicationContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** True if Wireless debugging is currently on. Never throws. */
    fun isOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.applicationContext.contentResolver, SETTING, 0) == 1
    } catch (_: Exception) {
        false
    }

    /** Turns Wireless debugging on if it is off. True only when this call switched it on. */
    fun ensureEnabled(context: Context): Boolean {
        val app = context.applicationContext
        try {
            if (!JarvisPrefs.getBoolean(PREF_ENABLED, true)) return false
            if (isOn(app)) return false
            if (!canRepair(app)) {
                Log.i(TAG, "skip: WRITE_SECURE_SETTINGS not granted")
                return false
            }
            val ok = Settings.Global.putInt(app.contentResolver, SETTING, 1) && isOn(app)
            Log.i(TAG, "re-enabled ok=$ok")
            return ok
        } catch (e: Exception) {
            Log.w(TAG, "repair failed: ${e.javaClass.simpleName}")
            return false
        }
    }
}
