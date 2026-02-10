package com.jarvismini.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Helper class to handle storage permissions for Android 11+ (Scoped Storage)
 * This is needed to access /storage/emulated/0/JarvisModels/
 * 
 * CRITICAL FIX: This version properly handles MANAGE_EXTERNAL_STORAGE for Android 11+
 */
object StoragePermissionHelper {
    
    private const val TAG = "StoragePermissionHelper"
    const val REQUEST_CODE_STORAGE = 1001
    const val REQUEST_CODE_MANAGE_STORAGE = 1002
    
    /**
     * Check if we have permission to access external storage for models
     * 
     * For Android 11+ (API 30+): Requires MANAGE_EXTERNAL_STORAGE
     * For Android 6-10: Requires READ_EXTERNAL_STORAGE
     * For Android 5 and below: Permission granted at install time
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE for full access to /storage/emulated/0
            val hasPermission = Environment.isExternalStorageManager()
            Log.d(TAG, "Android 11+ - MANAGE_EXTERNAL_STORAGE: $hasPermission")
            hasPermission
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 requires READ_EXTERNAL_STORAGE
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Android 6-10 - READ_EXTERNAL_STORAGE: $hasPermission")
            hasPermission
        } else {
            // Below Android 6, permissions are granted at install time
            Log.d(TAG, "Android 5 or below - Permission granted at install")
            true
        }
    }
    
    /**
     * Legacy method name for backward compatibility
     */
    fun hasStoragePermissions(context: Context): Boolean {
        return hasStoragePermission(context)
    }
    
    /**
     * Request storage permission based on Android version
     * 
     * Android 11+: Opens settings to request MANAGE_EXTERNAL_STORAGE
     * Android 6-10: Shows runtime permission dialog for READ_EXTERNAL_STORAGE
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Request MANAGE_EXTERNAL_STORAGE via settings
            try {
                Log.i(TAG, "Requesting MANAGE_EXTERNAL_STORAGE permission for Android 11+")
                
                // Show explanation toast
                Toast.makeText(
                    activity,
                    "Please grant 'All Files Access' permission to load AI models",
                    Toast.LENGTH_LONG
                ).show()
                
                // Open app-specific all files access settings
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening app-specific all files access settings", e)
                try {
                    // Fallback to general manage all files access settings
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                } catch (e2: Exception) {
                    Log.e(TAG, "Error opening general manage all files access settings", e2)
                    Toast.makeText(
                        activity,
                        "Please manually grant 'All Files Access' in Settings → Apps → Jarvis",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            // Android 6-10: Request READ_EXTERNAL_STORAGE via runtime permission
            Log.i(TAG, "Requesting READ_EXTERNAL_STORAGE permission for Android 6-10")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_CODE_STORAGE
            )
        }
    }
    
    /**
     * Legacy method name for backward compatibility
     */
    fun requestStoragePermissions(activity: Activity) {
        requestStoragePermission(activity)
    }
    
    /**
     * Handle permission request result
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        onGranted: () -> Unit = {},
        onDenied: () -> Unit = {}
    ) {
        if (requestCode == REQUEST_CODE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Storage permission granted")
                onGranted()
            } else {
                Log.w(TAG, "Storage permission denied")
                onDenied()
            }
        }
    }
    
    /**
     * Get the models directory path
     * Returns: /storage/emulated/0/JarvisModels
     * 
     * This uses Environment.getExternalStorageDirectory() which properly resolves to
     * /storage/emulated/0 on all Android versions
     */
    fun getModelsDirectory(): File {
        val externalStorage = Environment.getExternalStorageDirectory()
        val modelsDir = File(externalStorage, "JarvisModels")
        
        Log.d(TAG, "=== MODELS DIRECTORY INFO ===")
        Log.d(TAG, "External storage path: ${externalStorage.absolutePath}")
        Log.d(TAG, "Models directory path: ${modelsDir.absolutePath}")
        Log.d(TAG, "Directory exists: ${modelsDir.exists()}")
        Log.d(TAG, "Directory readable: ${modelsDir.canRead()}")
        Log.d(TAG, "Directory writable: ${modelsDir.canWrite()}")
        
        return modelsDir
    }
    
    /**
     * Get the models directory with context (same as above, context not needed)
     */
    fun getModelsDirectory(context: Context): File {
        return getModelsDirectory()
    }
    
    /**
     * Check if models directory exists and list its contents for debugging
     * Returns a detailed debug string
     */
    fun debugModelsDirectory(): String {
        val modelsDir = getModelsDirectory()
        val sb = StringBuilder()
        
        sb.append("=== JARVIS MODELS DIRECTORY DEBUG ===\n\n")
        
        // Basic info
        sb.append("Path: ${modelsDir.absolutePath}\n")
        sb.append("Exists: ${modelsDir.exists()}\n")
        sb.append("Is Directory: ${modelsDir.isDirectory}\n")
        sb.append("Can Read: ${modelsDir.canRead()}\n")
        sb.append("Can Write: ${modelsDir.canWrite()}\n\n")
        
        // Permission status
        sb.append("=== PERMISSION STATUS ===\n")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasManagePermission = Environment.isExternalStorageManager()
            sb.append("Android Version: ${Build.VERSION.SDK_INT} (Android 11+)\n")
            sb.append("MANAGE_EXTERNAL_STORAGE: $hasManagePermission\n")
            if (!hasManagePermission) {
                sb.append("⚠️ ALL FILES ACCESS NOT GRANTED!\n")
                sb.append("Go to: Settings → Apps → Jarvis → Permissions\n")
                sb.append("Enable: 'All files access' or 'Manage all files'\n")
            }
        } else {
            sb.append("Android Version: ${Build.VERSION.SDK_INT} (Pre-Android 11)\n")
            sb.append("READ_EXTERNAL_STORAGE: Required\n")
        }
        sb.append("\n")
        
        // List files
        if (modelsDir.exists() && modelsDir.isDirectory) {
            val files = modelsDir.listFiles()
            if (files != null && files.isNotEmpty()) {
                sb.append("=== FOUND ${files.size} FILES ===\n")
                files.sortedBy { it.name }.forEach { file ->
                    val size = if (file.isFile) {
                        val mb = file.length() / (1024.0 * 1024.0)
                        String.format("%.2f MB", mb)
                    } else {
                        "DIR"
                    }
                    val readable = if (file.canRead()) "✓" else "✗"
                    sb.append("  $readable ${file.name} ($size)\n")
                }
                sb.append("\n")
                
                // Check for expected models
                sb.append("=== EXPECTED MODELS ===\n")
                val expectedModels = listOf(
                    "phi-2.Q4_K_M.gguf",
                    "deepseek-coder-1.3b-instruct.Q4_K_M.gguf"
                )
                expectedModels.forEach { modelName ->
                    val found = files.any { it.name == modelName }
                    val status = if (found) "✓ FOUND" else "✗ MISSING"
                    sb.append("  $status $modelName\n")
                }
            } else {
                sb.append("⚠️ DIRECTORY IS EMPTY OR NOT READABLE\n")
                sb.append("Please place model files in:\n")
                sb.append("${modelsDir.absolutePath}\n")
            }
        } else {
            sb.append("⚠️ DIRECTORY DOES NOT EXIST\n")
            sb.append("Please create directory:\n")
            sb.append("${modelsDir.absolutePath}\n")
            sb.append("And place model files there.\n")
        }
        
        val result = sb.toString()
        Log.i(TAG, result)
        return result
    }
    
    /**
     * Create models directory if it doesn't exist
     */
    fun ensureModelsDirectoryExists(): Boolean {
        val modelsDir = getModelsDirectory()
        
        return if (!modelsDir.exists()) {
            try {
                val created = modelsDir.mkdirs()
                Log.i(TAG, "Created models directory: $created at ${modelsDir.absolutePath}")
                created
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create models directory", e)
                false
            }
        } else {
            Log.d(TAG, "Models directory already exists at ${modelsDir.absolutePath}")
            true
        }
    }
    
    /**
     * Check if specific model file exists
     */
    fun doesModelExist(modelFilename: String): Boolean {
        val modelsDir = getModelsDirectory()
        val modelFile = File(modelsDir, modelFilename)
        val exists = modelFile.exists() && modelFile.isFile && modelFile.canRead()
        Log.d(TAG, "Model '$modelFilename' exists: $exists (path: ${modelFile.absolutePath})")
        return exists
    }
    
    /**
     * Get path to specific model file
     */
    fun getModelPath(modelFilename: String): String {
        val modelsDir = getModelsDirectory()
        val modelFile = File(modelsDir, modelFilename)
        return modelFile.absolutePath
    }
}
