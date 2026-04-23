package com.gtky.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object CameraPermission {
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns true if the user has permanently blocked future system prompts
     * (checked "Don't ask again" or is on Android 11+ where two denials auto-block).
     * Only meaningful when the permission is currently NOT granted.
     */
    fun isPermanentlyBlocked(activity: Activity): Boolean {
        val granted = isGranted(activity)
        if (granted) return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.CAMERA
        )
    }
}
