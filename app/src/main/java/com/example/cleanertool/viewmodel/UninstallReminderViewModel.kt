package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanertool.data.AppInfo
import com.example.cleanertool.utils.AppUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UnusedApp(
    val appInfo: AppInfo,
    val reason: String,
    val priority: Int // Higher priority = more likely to be unused
)

class UninstallReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val _unusedApps = MutableStateFlow<List<UnusedApp>>(emptyList())
    val unusedApps: StateFlow<List<UnusedApp>> = _unusedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun analyzeUnusedApps(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val allApps = AppUtils.getInstalledApps(context)
                val unusedAppsList = mutableListOf<UnusedApp>()

                val packageManager = context.packageManager

                allApps.forEach { app ->
                    if (app.isSystemApp) return@forEach // Skip system apps

                    var priority = 0
                    val reasons = mutableListOf<String>()

                    // Check app size (large apps)
                    if (app.size > 100 * 1024 * 1024) { // > 100 MB
                        priority += 2
                        reasons.add("Large size (${formatFileSize(app.size)})")
                    }

                    // Check if app is rarely used (using package manager stats if available)
                    try {
                        val usageStats = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                            usageStatsManager?.queryUsageStats(
                                android.app.usage.UsageStatsManager.INTERVAL_BEST,
                                System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000), // Last 30 days
                                System.currentTimeMillis()
                            )?.find { it.packageName == app.packageName }
                        } else {
                            null
                        }

                        if (usageStats == null || usageStats.totalTimeInForeground < 60000) { // Less than 1 minute in 30 days
                            priority += 5
                            reasons.add("Rarely used")
                        }
                    } catch (e: Exception) {
                        // Usage stats permission might not be granted
                    }

                    // Check if app hasn't been updated recently (older version might indicate unused)
                    // This is a heuristic - newer apps are more likely to be used

                    if (priority > 0) {
                        unusedAppsList.add(
                            UnusedApp(
                                appInfo = app,
                                reason = reasons.joinToString(", "),
                                priority = priority
                            )
                        )
                    }
                }

                // Sort by priority (highest first)
                _unusedApps.value = unusedAppsList.sortedByDescending { it.priority }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to analyze apps"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Remove an app from the unused apps list after it was uninstalled (confirmed by system).
     */
    fun markAppUninstalled(packageName: String) {
        _unusedApps.value = _unusedApps.value.filter { it.appInfo.packageName != packageName }
    }
}

