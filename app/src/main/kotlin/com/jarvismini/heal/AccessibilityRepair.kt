package com.jarvismini.heal

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * AccessibilityRepair: puts Jarvis's own accessibility services back into
 * ENABLED_ACCESSIBILITY_SERVICES when Android drops them (force stop, reinstall/update,
 * OEM battery cleanup) so the a11y engine rebinds without a trip to Settings.
 *
 * Android only lets an app write that setting if it holds WRITE_SECURE_SETTINGS, which can't be
 * requested at runtime. Grant it once from adb / Termux:
 *
 *   adb shell pm grant com.jarvismini android.permission.WRITE_SECURE_SETTINGS
 *
 * The grant survives reboots and force stops. Without it [ensureEnabled] is a harmless no-op that
 * returns false. Only ever adds Jarvis's own entries; other enabled services are left untouched.
 */
object AccessibilityRepair {

    private const val TAG = "A11yRepair"

    private val OURS = listOf(
        "com.jarvismini.automation.AppAutomationService",
        "com.jarvismini.callhandler.accessibility.CallAccessibilityService",
    )

    fun canRepair(context: Context): Boolean =
        context.applicationContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** True if every Jarvis accessibility service is enabled in system settings. Never throws. */
    fun allEnabled(context: Context): Boolean = try {
        val app = context.applicationContext
        val enabled = enabledSet(app)
        OURS.all { ComponentName(app.packageName, it).flattenToString() in enabled }
    } catch (_: Exception) {
        false
    }

    /** Re-adds any missing Jarvis service. True if all are (now) enabled. Never throws. */
    fun ensureEnabled(context: Context): Boolean {
        val app = context.applicationContext
        try {
            if (allEnabled(app)) return true
            if (!canRepair(app)) {
                Log.i(TAG, "skip: WRITE_SECURE_SETTINGS not granted")
                return false
            }
            val cr = app.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val merged = (current.split(':').map { it.trim() }.filter { it.isNotEmpty() } +
                OURS.map { ComponentName(app.packageName, it).flattenToString() })
                .distinct()
                .joinToString(":")
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, merged)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            val ok = allEnabled(app)
            Log.i(TAG, "repair done ok=$ok")
            return ok
        } catch (e: Exception) {
            Log.w(TAG, "repair failed: ${e.javaClass.simpleName}")
            return false
        }
    }

    private fun enabledSet(app: Context): Set<String> {
        val raw = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        // Android may store either the short (pkg/.Cls) or flattened (pkg/pkg.Cls) form; normalise both.
        return raw.split(':').mapNotNull { ComponentName.unflattenFromString(it.trim())?.flattenToString() }.toSet()
    }
}
