package com.example.cleanertool.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.cleanertool.utils.NotificationManager
import com.example.cleanertool.utils.SettingsPreferencesManager

class UninstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
            val settingsPrefs = SettingsPreferencesManager(context)
            
            if (!settingsPrefs.getUninstallReminder()) return

            val packageName = intent.data?.schemeSpecificPart
            if (packageName != null && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                // App was uninstalled (not replaced)
                try {
                    val packageManager = context.packageManager
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    
                    NotificationManager.showUninstallNotification(context, appName)
                } catch (e: Exception) {
                    // App info not available, use package name
                    NotificationManager.showUninstallNotification(context, packageName)
                }
            }
        }
    }
}

