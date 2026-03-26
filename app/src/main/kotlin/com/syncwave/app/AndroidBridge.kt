package com.syncwave.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class AndroidBridge(private val context: Context, private val activity: MainActivity) {

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("SyncWave", text))
        Toast.makeText(context, "Room code copied!", Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    /** Called by JS when user enables BG PLAY */
    @JavascriptInterface
    fun enableBgPlay() {
        activity.runOnUiThread { activity.startBgService() }
    }

    /** Called by JS when user disables BG PLAY */
    @JavascriptInterface
    fun disableBgPlay() {
        activity.runOnUiThread { activity.stopBgService() }
    }
}
