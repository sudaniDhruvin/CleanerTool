package com.example.cleanertool.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val versionName: String?,
    val versionCode: Long,
    val size: Long,
    val isSystemApp: Boolean
)

