package com.jarvismini

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.jarvismini.core.JarvisState
import com.jarvismini.core.StoragePermissionHelper
import com.jarvismini.core.routine.FloatingTimerService
import com.jarvismini.core.stopwatch.NotificationPermissionHelper
import com.jarvismini.engine.EngineProvider
import com.jarvismini.ui.main.MainScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE_OVERLAY = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Jarvis state
        JarvisState.init(applicationContext)

        // Initialize progress system
        ProgressInitializer.registerAllBlocks(this)

        // Request notification permission on startup if needed (Android 13+)
        if (NotificationPermissionHelper.shouldRequestPermission(this)) {
            NotificationPermissionHelper.requestPermission(this)
        }

        // NEW: Request "Display over other apps" permission for floating timer overlay
        // If denied, the notification timer still works — overlay is simply skipped.
        requestOverlayPermissionIfNeeded()

        // CRITICAL FIX: Request storage permissions if not granted
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

    /**
     * Sends the user to the system "Display over other apps" settings screen
     * if the permission has not been granted yet. Only needs to be done once.
     */
    private fun requestOverlayPermissionIfNeeded() {
        if (!FloatingTimerService.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Grant 'Display over other apps' for floating timer overlay",
                Toast.LENGTH_LONG
            ).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_CODE_OVERLAY)
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

        if (requestCode == NotificationPermissionHelper.REQUEST_CODE_POST_NOTIFICATIONS) {
            if (NotificationPermissionHelper.isPermissionGranted(this)) {
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied.", Toast.LENGTH_LONG).show()
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

        // Overlay permission result
        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (FloatingTimerService.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted. Floating timer enabled!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay denied. Timer still works via notification.", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == StoragePermissionHelper.REQUEST_CODE_MANAGE_STORAGE) {
            if (StoragePermissionHelper.hasStoragePermission(this)) {
                Toast.makeText(this, "All Files Access granted! Loading AI models...", Toast.LENGTH_SHORT).show()
                initializeEngine()
            } else {
                Toast.makeText(this, "All Files Access is required to load AI models.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
