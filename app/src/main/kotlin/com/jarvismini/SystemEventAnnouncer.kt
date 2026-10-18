package com.jarvismini

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.ContextCompat
import com.jarvismini.core.AssistantProfile
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.announce.AnnouncePriority
import com.jarvismini.core.announce.AnnouncementEngine
import com.jarvismini.core.events.EventLog
import com.jarvismini.core.events.EventType

/** Spec section 17: battery issues, network reconnect, app crashes. Every event is logged, then announced through the gate. */
object SystemEventAnnouncer {

    private const val KEY_CRASH = "last_crash_info"
    @Volatile private var lostAt = 0L
    private var installed = false

    fun install(app: Context) {
        if (installed) return
        installed = true
        installCrashHook()
        installBattery(app)
        installNetwork(app)
        reportPreviousCrash()
    }

    private fun installCrashHook() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                JarvisPrefs.putString(KEY_CRASH, e.javaClass.simpleName + " @ " + System.currentTimeMillis())
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    private fun reportPreviousCrash() {
        val info = JarvisPrefs.getString(KEY_CRASH) ?: return
        if (info.isBlank()) return
        JarvisPrefs.remove(KEY_CRASH)
        EventLog.add(EventType.SERVICE, "App crashed and restarted", info)
        AnnouncementEngine.announce(
            "I crashed earlier and have recovered, " + AssistantProfile.addressUser() + ".",
            AnnouncePriority.HIGH, "app_crash"
        )
    }

    private fun installBattery(app: Context) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_BATTERY_LOW -> {
                        EventLog.add(EventType.BATTERY, "Battery low")
                        AnnouncementEngine.announce(
                            "Battery is low, " + AssistantProfile.addressUser() + ". Consider charging.",
                            AnnouncePriority.HIGH, "battery_low"
                        )
                    }
                    Intent.ACTION_BATTERY_OKAY -> EventLog.add(EventType.BATTERY, "Battery recovered")
                    Intent.ACTION_POWER_CONNECTED -> EventLog.add(EventType.BATTERY, "Charger connected")
                    Intent.ACTION_POWER_DISCONNECTED -> EventLog.add(EventType.BATTERY, "Charger disconnected")
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(app, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun installNetwork(app: Context) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) { lostAt = System.currentTimeMillis() }
                override fun onAvailable(network: Network) {
                    val lost = lostAt
                    if (lost == 0L) return
                    lostAt = 0L
                    val down = System.currentTimeMillis() - lost
                    if (down < 5_000L) return
                    EventLog.add(EventType.SERVICE, "Network reconnected", "down " + down / 1000 + "s")
                    AnnouncementEngine.announce("Network connection restored.", AnnouncePriority.NORMAL, "net_back")
                }
            })
        } catch (_: Exception) {}
    }
}
