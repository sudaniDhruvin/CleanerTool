package com.example.cleanertool.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

object OverlayPermission {
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // No restriction below Marshmallow
        }
    }
}
