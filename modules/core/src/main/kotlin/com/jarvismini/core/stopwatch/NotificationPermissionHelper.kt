package com.jarvismini.core.stopwatch

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper for requesting notification permission on Android 13+ (API 33+)
 */
object NotificationPermissionHelper {
    
    const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    
    /**
     * Check if we need to request POST_NOTIFICATIONS permission
     * Only needed on Android 13+ (API 33+)
     */
    fun shouldRequestPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            !isPermissionGranted(context)
        } else {
            false
        }
    }
    
    /**
     * Check if POST_NOTIFICATIONS permission is granted
     */
    fun isPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * Request POST_NOTIFICATIONS permission from the user
     */
    fun requestPermission(activity: Activity, requestCode: Int = REQUEST_CODE_POST_NOTIFICATIONS) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }
    
    /**
     * Check if the user previously denied the permission and should see a rationale
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            false
        }
    }
}
