package com.jarvismini

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.jarvismini.core.JarvisState
import com.jarvismini.core.StoragePermissionHelper
import com.jarvismini.core.stopwatch.NotificationPermissionHelper
import com.jarvismini.engine.EngineProvider
import com.jarvismini.ui.main.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        JarvisState.init(applicationContext)
        EngineProvider.init(applicationContext)

        ProgressInitializer.registerAllBlocks(this)

        // Request notification permission on startup if needed (Android 13+)
        if (NotificationPermissionHelper.shouldRequestPermission(this)) {
            NotificationPermissionHelper.requestPermission(this)
        }

        // Request storage permissions if not granted
        if (!StoragePermissionHelper.hasStoragePermissions(this)) {
            StoragePermissionHelper.requestStoragePermissions(this)
        }

        setContent {
            MaterialTheme {
                Surface {
                    MainScreen()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Handle notification permission
        if (requestCode == NotificationPermissionHelper.REQUEST_CODE_POST_NOTIFICATIONS) {
            if (NotificationPermissionHelper.isPermissionGranted(this)) {
                Toast.makeText(
                    this,
                    "Notification permission granted! Stopwatch will work in background.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Notification permission denied. Stopwatch notifications won't be visible.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Handle storage permission
        StoragePermissionHelper.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults,
            onGranted = {
                Toast.makeText(
                    this,
                    "Storage permission granted!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDenied = {
                Toast.makeText(
                    this,
                    "Storage permission denied. Some features may not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}
