package com.example.cleanertool.utils

import android.Manifest
import android.os.Build

object PermissionUtils {
    fun getStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    fun getAllRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        // Storage permissions
        permissions.addAll(getStoragePermissions().toList())
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Audio recording permission (for speaker maintenance)
        permissions.add(Manifest.permission.RECORD_AUDIO)
        
        return permissions
    }
    
    fun getNotificationPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    }
    
    fun getAudioPermission(): String {
        return Manifest.permission.RECORD_AUDIO
    }
}

