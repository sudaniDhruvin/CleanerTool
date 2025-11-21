package com.example.cleanertool.utils

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecentApp(
    val packageName: String,
    val appName: String,
    val lastTimeUsed: Long,
    val totalTimeInForeground: Long
)

object UsageStatsUtils {
    /**
     * Check if Usage Access Permission is granted
     */
    fun isUsageAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60 * 60, // Last hour
                time
            )
            stats != null && stats.isNotEmpty()
        } else {
            false
        }
    }

    /**
     * Open Usage Access Settings screen
     */
    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        context.startActivity(intent)
    }

    /**
     * Get recently used apps using UsageStatsManager
     * Requires Usage Access Permission
     */
    suspend fun getRecentUsedApps(context: Context, hoursBack: Long = 24): List<RecentApp> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return@withContext emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext emptyList()

        val packageManager = context.packageManager
        val time = System.currentTimeMillis()
        val startTime = time - (hoursBack * 60 * 60 * 1000) // hoursBack hours ago

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            time
        ) ?: return@withContext emptyList()

        val recentAppsMap = mutableMapOf<String, UsageStats>()

        // Aggregate stats by package name (in case there are multiple entries)
        stats.forEach { usageStat ->
            if (usageStat.lastTimeUsed > 0) {
                val existing = recentAppsMap[usageStat.packageName]
                if (existing == null || usageStat.lastTimeUsed > existing.lastTimeUsed) {
                    recentAppsMap[usageStat.packageName] = usageStat
                }
            }
        }

        // Convert to RecentApp list
        recentAppsMap.values.mapNotNull { usageStat ->
            try {
                val appInfo = packageManager.getApplicationInfo(usageStat.packageName, 0)
                
                // Skip system apps (optional - you can remove this if you want to show all apps)
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                // Skip core Android system apps
                if (isSystemApp && !isUpdatedSystemApp) {
                    val coreSystemPackages = listOf(
                        "android",
                        "com.android.systemui",
                        "com.android.providers.",
                        "com.android.server.",
                        "com.android.phone",
                        "com.android.settings",
                        "com.android.launcher"
                    )
                    if (coreSystemPackages.any { usageStat.packageName.startsWith(it) }) {
                        return@mapNotNull null
                    }
                }

                val appName = packageManager.getApplicationLabel(appInfo).toString()

                RecentApp(
                    packageName = usageStat.packageName,
                    appName = appName,
                    lastTimeUsed = usageStat.lastTimeUsed,
                    totalTimeInForeground = usageStat.totalTimeInForeground
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.lastTimeUsed }
    }

    /**
     * Get installed apps (all apps on device)
     */
    suspend fun getInstalledApps(context: Context): List<RecentApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        apps.mapNotNull { appInfo ->
            try {
                // Skip system apps
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                if (isSystemApp && !isUpdatedSystemApp) {
                    val coreSystemPackages = listOf(
                        "android",
                        "com.android.systemui",
                        "com.android.providers.",
                        "com.android.server.",
                        "com.android.phone",
                        "com.android.settings",
                        "com.android.launcher"
                    )
                    if (coreSystemPackages.any { appInfo.packageName.startsWith(it) }) {
                        return@mapNotNull null
                    }
                }

                val appName = packageManager.getApplicationLabel(appInfo).toString()

                RecentApp(
                    packageName = appInfo.packageName,
                    appName = appName,
                    lastTimeUsed = 0L, // Not available for installed apps
                    totalTimeInForeground = 0L
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName }
    }
}

