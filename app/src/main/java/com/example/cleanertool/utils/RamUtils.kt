package com.example.cleanertool.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.app.ActivityManager.RunningServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RamInfo(
    val totalRam: Long,
    val usedRam: Long,
    val freeRam: Long,
    val ramUsagePercentage: Int
)

data class RunningApp(
    val packageName: String,
    val appName: String,
    val memoryUsage: Long
)

object RamUtils {
    suspend fun getRamInfo(context: Context): RamInfo = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            memInfo.totalMem
        } else {
            // Fallback for older versions
            Runtime.getRuntime().maxMemory()
        }

        val usedRam = totalRam - memInfo.availMem
        val freeRam = memInfo.availMem
        val ramUsagePercentage = ((usedRam.toDouble() / totalRam.toDouble()) * 100).toInt()

        RamInfo(
            totalRam = totalRam,
            usedRam = usedRam,
            freeRam = freeRam,
            ramUsagePercentage = ramUsagePercentage
        )
    }

    suspend fun getRunningApps(context: Context): List<RunningApp> = withContext(Dispatchers.IO) {
        val runningAppsMap = mutableMapOf<String, RunningApp>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val packageManager = context.packageManager

        try {
            val runningProcesses = activityManager.runningAppProcesses
            if (runningProcesses == null || runningProcesses.isEmpty()) {
                android.util.Log.w("RamUtils", "No running processes found - runningAppProcesses returned null or empty")
                // Fallback: try to get installed apps that might be running
                return@withContext getInstalledAppsAsRunningApps(context, packageManager)
            }
            
            android.util.Log.d("RamUtils", "Found ${runningProcesses.size} running processes")

            runningProcesses.forEach { processInfo ->
                try {
                    // Check all packages in this process, not just the first one
                    processInfo.pkgList.forEach { packageName ->
                        try {
                            // Skip only the absolute core system process
                            if (packageName == "system" || packageName == "android") {
                                android.util.Log.d("RamUtils", "Skipped core process: $packageName")
                                return@forEach
                            }

                            val appInfo = try {
                                packageManager.getApplicationInfo(packageName, 0)
                            } catch (e: Exception) {
                                android.util.Log.d("RamUtils", "Could not get app info for: $packageName - ${e.message}")
                                return@forEach
                            }

                            // Show ALL apps that are running, including system apps
                            // Only skip truly core Android system processes that are essential
                            // This matches what other task manager apps show
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            
                            // Only skip if it's a core Android system app AND it's in our exclusion list
                            if (isSystemApp) {
                                // Very minimal exclusion list - only skip essential Android system processes
                                val coreSystemPackages = listOf(
                                    "android",
                                    "com.android.systemui",
                                    "com.android.providers.settings",
                                    "com.android.providers.contacts",
                                    "com.android.providers.media",
                                    "com.android.server.",
                                    "com.android.phone",
                                    "com.android.keychain"
                                )
                                // Only skip if it matches exactly or starts with these core packages
                                val shouldSkip = coreSystemPackages.any { 
                                    packageName == it || packageName.startsWith(it)
                                }
                                if (shouldSkip) {
                                    android.util.Log.d("RamUtils", "Skipped core system app: $packageName")
                                    return@forEach
                                }
                            }
                            
                            // Include ALL running apps (don't filter by importance)
                            // Show all apps that are running, including foreground, background, cached, etc.
                            // This matches what other task manager apps show
                            val importance = processInfo.importance
                            android.util.Log.d("RamUtils", "Processing app: $packageName, importance: $importance")
                            
                            // Include all apps - don't filter by importance
                            // This ensures we show all running apps like Truecaller, Slack, etc.
                            val appName = packageManager.getApplicationLabel(appInfo).toString()
                            
                            // Get memory usage
                            val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                            val memoryUsage = if (memoryInfo.isNotEmpty()) {
                                memoryInfo[0].totalPss * 1024L // Convert KB to bytes
                            } else {
                                // Fallback: estimate based on importance
                                when {
                                    importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> 50 * 1024 * 1024L // 50MB estimate
                                    else -> 20 * 1024 * 1024L // 20MB estimate
                                }
                            }

                            // Use package name as key to avoid duplicates
                            // If app already exists, keep the one with higher memory usage
                            val existing = runningAppsMap[packageName]
                            if (existing == null || memoryUsage > existing.memoryUsage) {
                                runningAppsMap[packageName] = RunningApp(
                                    packageName = packageName,
                                    appName = appName,
                                    memoryUsage = memoryUsage
                                )
                                android.util.Log.d("RamUtils", "Added app: $appName ($packageName), importance: $importance, memory: ${memoryUsage / 1024 / 1024}MB")
                            }
                        } catch (e: Exception) {
                            // Skip this package, continue with next
                        }
                    }
                } catch (e: Exception) {
                    // Skip this process, continue with next
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("RamUtils", "Error getting running apps: ${e.message}", e)
        }
        
        android.util.Log.d("RamUtils", "Found ${runningAppsMap.size} unique running apps from processes")

        // Also try to get apps from running services (alternative method for older Android)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // For older Android versions, also check running services
                val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
                runningServices.forEach { serviceInfo ->
                    try {
                        val packageName = serviceInfo.service.packageName
                        
                        // Skip if already added
                        if (runningAppsMap.containsKey(packageName)) {
                            return@forEach
                        }
                        
                        // Skip core system packages
                        if (packageName == "system" || 
                            packageName == "android" ||
                            packageName.startsWith("com.android.providers.") ||
                            packageName.startsWith("com.android.server.")) {
                            return@forEach
                        }
                        
                        val appInfo = try {
                            packageManager.getApplicationInfo(packageName, 0)
                        } catch (e: Exception) {
                            return@forEach
                        }
                        
                        // Skip core system apps
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
                            if (coreSystemPackages.any { packageName.startsWith(it) }) {
                                return@forEach
                            }
                        }
                        
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        
                        // Estimate memory usage for services
                        val memoryUsage = 30 * 1024 * 1024L // 30MB estimate for services
                        
                        runningAppsMap[packageName] = RunningApp(
                            packageName = packageName,
                            appName = appName,
                            memoryUsage = memoryUsage
                        )
                    } catch (e: Exception) {
                        // Skip this service
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RamUtils", "Error getting running services: ${e.message}", e)
        }

        // If we have very few apps, also add installed apps to show more options
        // This helps when runningAppProcesses is restricted on newer Android versions
        if (runningAppsMap.size < 5) {
            android.util.Log.d("RamUtils", "Only ${runningAppsMap.size} apps found, adding installed apps")
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            installedApps.forEach { appInfo ->
                try {
                    // Skip if already added
                    if (runningAppsMap.containsKey(appInfo.packageName)) {
                        return@forEach
                    }
                    
                    // Skip core system apps
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSystemApp) {
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
                            return@forEach
                        }
                    }
                    
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    runningAppsMap[appInfo.packageName] = RunningApp(
                        packageName = appInfo.packageName,
                        appName = appName,
                        memoryUsage = 0L // Unknown - not actually running
                    )
                } catch (e: Exception) {
                    // Skip
                }
            }
        }
        
        val finalList = runningAppsMap.values.sortedByDescending { it.memoryUsage }
        android.util.Log.d("RamUtils", "Returning ${finalList.size} apps (${runningAppsMap.size - finalList.size} from installed apps)")
        
        // If still no apps found, fallback to showing installed apps
        if (finalList.isEmpty()) {
            android.util.Log.w("RamUtils", "No apps found, falling back to installed apps")
            val fallbackApps = getInstalledAppsAsRunningApps(context, packageManager)
            return@withContext fallbackApps
        }
        
        return@withContext finalList
    }
    
    /**
     * Fallback: Get installed apps as running apps (when runningAppProcesses is not available)
     */
    private fun getInstalledAppsAsRunningApps(context: Context, packageManager: PackageManager): List<RunningApp> {
        try {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val runningApps = mutableListOf<RunningApp>()
            
            installedApps.forEach { appInfo ->
                try {
                    // Skip core system apps
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSystemApp) {
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
                            return@forEach
                        }
                    }
                    
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    runningApps.add(
                        RunningApp(
                            packageName = appInfo.packageName,
                            appName = appName,
                            memoryUsage = 0L // Unknown memory usage
                        )
                    )
                } catch (e: Exception) {
                    // Skip this app
                }
            }
            
            android.util.Log.d("RamUtils", "Fallback: Found ${runningApps.size} installed apps")
            return runningApps.take(30) // Limit to 30 apps
        } catch (e: Exception) {
            android.util.Log.e("RamUtils", "Error in fallback: ${e.message}", e)
            return emptyList()
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

