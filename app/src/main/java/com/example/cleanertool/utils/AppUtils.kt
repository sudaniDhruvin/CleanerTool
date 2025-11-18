package com.example.cleanertool.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.example.cleanertool.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppUtils {
    suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        apps.mapNotNull { appInfo ->
            try {
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo.packageName)
                val packageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
                
                val size = try {
                    val appDir = context.packageManager.getApplicationInfo(
                        appInfo.packageName,
                        0
                    ).sourceDir
                    java.io.File(appDir).length()
                } catch (e: Exception) {
                    0L
                }
                
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = appName,
                    icon = icon,
                    versionName = packageInfo.versionName,
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    },
                    size = size,
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName }
    }
}

