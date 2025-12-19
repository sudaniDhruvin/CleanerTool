package com.example.cleanertool.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.cleanertool.services.CallOverlayService
import com.example.cleanertool.utils.NotificationManager
import com.example.cleanertool.utils.OverlayPermission
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
                    maybeShowUninstallOverlay(context, appName)
                } catch (e: Exception) {
                    // App info not available, use package name
                    NotificationManager.showUninstallNotification(context, packageName)
                    maybeShowUninstallOverlay(context, packageName)
                }
            }
        }
    }

    private fun maybeShowUninstallOverlay(context: Context, appName: String) {
        if (!OverlayPermission.hasOverlayPermission(context)) return

        val overlayIntent = Intent(context, CallOverlayService::class.java).apply {
            putExtra(CallOverlayService.EXTRA_PRIMARY_TEXT, appName)
            putExtra(CallOverlayService.EXTRA_SECONDARY_TEXT, "Tap to clean leftovers")
            // Pass uninstall timestamp for accurate elapsed text
            putExtra(CallOverlayService.EXTRA_CALL_END_TIMESTAMP, System.currentTimeMillis())
            putExtra(CallOverlayService.EXTRA_MODE, CallOverlayService.MODE_UNINSTALL)
        }
        context.startService(overlayIntent)
    }
}

