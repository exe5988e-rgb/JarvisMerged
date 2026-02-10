package com.jarvismini

import android.content.Intent
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

        // Initialize Jarvis state
        JarvisState.init(applicationContext)
        
        // Initialize progress system
        ProgressInitializer.registerAllBlocks(this)

        // Request notification permission on startup if needed (Android 13+)
        if (NotificationPermissionHelper.shouldRequestPermission(this)) {
            NotificationPermissionHelper.requestPermission(this)
        }

        // CRITICAL FIX: Request storage permissions if not granted
        // This is required to access /storage/emulated/0/JarvisModels/ on Android 11+
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this)
        } else {
            // Permission already granted, initialize engine
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
     * Initialize the LLM engine
     * This loads the AI models from /storage/emulated/0/JarvisModels/
     */
    private fun initializeEngine() {
        try {
            EngineProvider.init(applicationContext)
            
            // Debug: Log models directory status
            val debug = StoragePermissionHelper.debugModelsDirectory()
            android.util.Log.i("MainActivity", "Models directory debug:\n$debug")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error initializing engine", e)
            Toast.makeText(
                this,
                "Error loading AI models: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
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

        // Handle storage permission (Android 6-10)
        StoragePermissionHelper.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults,
            onGranted = {
                Toast.makeText(
                    this,
                    "Storage permission granted! Loading AI models...",
                    Toast.LENGTH_SHORT
                ).show()
                // Initialize engine now that we have permission
                initializeEngine()
            },
            onDenied = {
                Toast.makeText(
                    this,
                    "Storage permission denied. AI models cannot be loaded.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
    
    /**
     * Handle result from Android 11+ "All Files Access" settings screen
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_MANAGE_STORAGE) {
            if (StoragePermissionHelper.hasStoragePermission(this)) {
                Toast.makeText(
                    this, 
                    "All Files Access granted! Loading AI models...", 
                    Toast.LENGTH_SHORT
                ).show()
                // Initialize engine now that we have permission
                initializeEngine()
            } else {
                Toast.makeText(
                    this,
                    "All Files Access is required to load AI models. Please grant permission in Settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
