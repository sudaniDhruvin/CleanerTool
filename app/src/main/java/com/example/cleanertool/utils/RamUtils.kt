package com.example.cleanertool.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
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
        val runningApps = mutableListOf<RunningApp>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val packageManager = context.packageManager

        try {
            val runningProcesses = activityManager.runningAppProcesses ?: return@withContext emptyList()

            runningProcesses.forEach { processInfo ->
                try {
                    val packageName = processInfo.pkgList.firstOrNull() ?: return@forEach
                    
                    // Skip system processes
                    if (packageName.startsWith("android.") || 
                        packageName.startsWith("com.android.") ||
                        packageName == "system" ||
                        packageName == "com.google.android.gms") {
                        return@forEach
                    }

                    val appInfo = try {
                        packageManager.getApplicationInfo(packageName, 0)
                    } catch (e: Exception) {
                        return@forEach
                    }

                    // Skip system apps
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                        return@forEach
                    }

                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    
                    // Get memory usage using getProcessMemoryInfo
                    val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                    val memoryUsage = if (memoryInfo.isNotEmpty()) {
                        memoryInfo[0].totalPss * 1024L // Convert KB to bytes
                    } else {
                        // Fallback: use importance level as rough estimate
                        processInfo.importance * 1024L * 100 // Rough estimate
                    }

                    runningApps.add(
                        RunningApp(
                            packageName = packageName,
                            appName = appName,
                            memoryUsage = memoryUsage
                        )
                    )
                } catch (e: Exception) {
                    // Skip apps that can't be processed
                }
            }

        } catch (e: Exception) {
            // Return empty list on error
        }

        // Sort by memory usage (descending) and return
        runningApps.sortedByDescending { it.memoryUsage }
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

