package com.jarvismini

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvismini.core.JarvisState
import com.jarvismini.core.StoragePermissionHelper
import com.jarvismini.core.stopwatch.NotificationPermissionHelper
import com.jarvismini.engine.EngineProvider
import com.jarvismini.ui.main.MainScreen
import com.jarvismini.ui.timer.FloatingTimerService
import com.jarvismini.voice.WakeWordService

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE_OVERLAY    = 1001
        private const val REQUEST_CODE_MIC        = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        JarvisState.init(applicationContext)
        ProgressInitializer.registerAllBlocks(this)

        if (NotificationPermissionHelper.shouldRequestPermission(this)) {
            NotificationPermissionHelper.requestPermission(this)
        }

        requestOverlayPermissionIfNeeded()
        requestMicPermissionIfNeeded()
        startWakeWordServiceIfPermitted()

        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this)
        } else {
            initializeEngine()
        }

        setContent {
            MaterialTheme {
                Surface {
                    MainScreen()
                }
            }
        }
    }

    private fun requestMicPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_MIC
            )
        }
    }

    /**
     * Wake word (Phase 2): starts WakeWordService automatically on every
     * launch, no toggle or manual step needed — but only if RECORD_AUDIO is
     * already granted. On a completely fresh install, the OS permission
     * dialog above must be answered by you at least once (this is an
     * Android OS requirement for any app, not something that can be
     * scripted around) — after that, every subsequent launch starts the
     * service automatically with zero action from you. onRequestPermissionsResult
     * below also calls this immediately if the mic permission is granted
     * mid-session, so you don't even need to relaunch after granting it.
     */
    private fun startWakeWordServiceIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, WakeWordService::class.java)
            )
        }
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (!FloatingTimerService.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Grant 'Display over other apps' for floating timer overlay",
                Toast.LENGTH_LONG
            ).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ),
                    REQUEST_CODE_OVERLAY
                )
            }
        }
    }

    private fun initializeEngine() {
        try {
            EngineProvider.init(applicationContext)
            val debug = StoragePermissionHelper.debugModelsDirectory()
            android.util.Log.i("MainActivity", "Models directory debug:\n$debug")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error initializing engine", e)
            Toast.makeText(this, "Error loading AI models: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_MIC -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission granted!", Toast.LENGTH_SHORT).show()
                    // Wake word (Phase 2): start immediately now that mic
                    // permission exists, instead of waiting for next launch.
                    startWakeWordServiceIfPermitted()
                } else {
                    Toast.makeText(this, "Microphone permission denied. Voice activation unavailable.", Toast.LENGTH_LONG).show()
                }
            }
            NotificationPermissionHelper.REQUEST_CODE_POST_NOTIFICATIONS -> {
                if (NotificationPermissionHelper.isPermissionGranted(this)) {
                    Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Notification permission denied.", Toast.LENGTH_LONG).show()
                }
            }
        }

        StoragePermissionHelper.onRequestPermissionsResult(
            requestCode, permissions, grantResults,
            onGranted = {
                Toast.makeText(this, "Storage permission granted! Loading AI models...", Toast.LENGTH_SHORT).show()
                initializeEngine()
            },
            onDenied = {
                Toast.makeText(this, "Storage permission denied. AI models cannot be loaded.", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (FloatingTimerService.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted. Floating timer enabled!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay denied. Timer still works via notification.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
