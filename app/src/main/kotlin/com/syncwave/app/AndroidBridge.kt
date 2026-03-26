package com.syncwave.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class AndroidBridge(private val context: Context) {

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
}
