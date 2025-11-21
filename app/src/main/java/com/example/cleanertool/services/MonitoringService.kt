package com.example.cleanertool.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import androidx.core.app.NotificationCompat
import com.example.cleanertool.R
import com.example.cleanertool.utils.NotificationManager
import com.example.cleanertool.utils.SettingsPreferencesManager
import com.example.cleanertool.utils.RamUtils
import com.example.cleanertool.utils.StorageUtils
import kotlinx.coroutines.*

class MonitoringService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitoring = false

    companion object {
        private const val CHANNEL_ID = "monitoring_service_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START_MONITORING = "com.example.cleanertool.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.cleanertool.STOP_MONITORING"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                if (!isMonitoring) {
                    isMonitoring = true
                    startMonitoring()
                }
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoring Service",
                AndroidNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background monitoring for device optimization"
            }
            val notificationManager = getSystemService(AndroidNotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cleaner Toolbox")
            .setContentText("Monitoring device performance")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring() {
        serviceScope.launch {
            // Track last notification times to avoid spam
            var lastStorageNotification = 0L
            var lastRamNotification = 0L
            var lastJunkNotification = 0L
            var lastBatteryCheck = 0L
            
            while (isMonitoring) {
                val currentTime = System.currentTimeMillis()
                
                // Check storage every 10 minutes
                if (currentTime - lastStorageNotification > 10 * 60 * 1000) {
                    checkStorage()
                    lastStorageNotification = currentTime
                }
                
                // Check RAM every 5 minutes
                if (currentTime - lastRamNotification > 5 * 60 * 1000) {
                    checkRamUsage()
                    lastRamNotification = currentTime
                }
                
                // Check junk files every 15 minutes
                if (currentTime - lastJunkNotification > 15 * 60 * 1000) {
                    checkJunkFiles()
                    lastJunkNotification = currentTime
                }
                
                // Check battery status every 5 minutes (backup to receiver)
                if (currentTime - lastBatteryCheck > 5 * 60 * 1000) {
                    checkBatteryStatus()
                    lastBatteryCheck = currentTime
                }
                
                // Check photo compression status every 2 minutes
                checkPhotoCompressionStatus()
                
                delay(2 * 60 * 1000) // Check every 2 minutes
            }
        }
    }

    private suspend fun checkJunkFiles() = withContext(Dispatchers.IO) {
        val settingsPrefs = SettingsPreferencesManager(this@MonitoringService)
        
        if (!settingsPrefs.getJunkReminder()) return@withContext

        try {
            // Simulate junk file check - in real implementation, use ScanViewModel logic
            // For now, we'll check if there are any large cache directories
            val cacheDir = cacheDir
            val cacheSize = getDirectorySize(cacheDir)
            
            // Threshold: 100MB
            if (cacheSize > 100 * 1024 * 1024) {
                NotificationManager.showJunkFileNotification(this@MonitoringService, cacheSize)
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private suspend fun checkRamUsage() = withContext(Dispatchers.IO) {
        val settingsPrefs = SettingsPreferencesManager(this@MonitoringService)
        
        if (!settingsPrefs.getRamReminder()) return@withContext

        try {
            val ramInfo = RamUtils.getRamInfo(this@MonitoringService)
            
            // Show notification if RAM usage is above 80%
            if (ramInfo.ramUsagePercentage >= 80) {
                NotificationManager.showHighRamNotification(
                    this@MonitoringService,
                    ramInfo.ramUsagePercentage
                )
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private suspend fun checkStorage() = withContext(Dispatchers.IO) {
        try {
            val storageInfo = StorageUtils.getStorageInfo(this@MonitoringService)
            
            // Show notification if storage is above 85% full
            if (storageInfo.usagePercentage >= 85) {
                NotificationManager.showStorageFullNotification(
                    this@MonitoringService,
                    storageInfo.freeSpace,
                    storageInfo.usagePercentage
                )
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private suspend fun checkBatteryStatus() = withContext(Dispatchers.IO) {
        val settingsPrefs = SettingsPreferencesManager(this@MonitoringService)
        
        try {
            val batteryIntent = registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            
            batteryIntent?.let {
                val level = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val batteryLevel = (level * 100 / scale.toFloat()).toInt()
                
                val status = it.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val isFull = status == android.os.BatteryManager.BATTERY_STATUS_FULL
                
                // Battery full notification (backup check)
                if (settingsPrefs.getChargingReminder() && isFull && batteryLevel >= 100) {
                    NotificationManager.showBatteryFullNotification(this@MonitoringService)
                }
                
                // Low battery notification (backup check)
                if (settingsPrefs.getLowBatteryReminder() && batteryLevel <= 20) {
                    NotificationManager.showLowBatteryNotification(this@MonitoringService, batteryLevel)
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private suspend fun checkPhotoCompressionStatus() = withContext(Dispatchers.IO) {
        try {
            val prefs = getSharedPreferences("compressed_images", Context.MODE_PRIVATE)
            val compressedUris = prefs.getStringSet("compressed_uris", emptySet()) ?: emptySet()
            val lastNotifiedCount = prefs.getInt("last_notified_compressed_count", 0)
            
            // If new images were compressed, show notification
            if (compressedUris.size > lastNotifiedCount) {
                val newCompressedCount = compressedUris.size - lastNotifiedCount
                
                // Calculate space saved (estimate)
                var totalSpaceSaved = 0L
                compressedUris.forEach { uriString ->
                    try {
                        val uri = android.net.Uri.parse(uriString)
                        val key = "size_${uri.toString().hashCode()}"
                        val originalSize = prefs.getLong("${key}_original", 0L)
                        val compressedSize = prefs.getLong("${key}_compressed", 0L)
                        if (originalSize > 0 && compressedSize > 0) {
                            totalSpaceSaved += (originalSize - compressedSize)
                        }
                    } catch (e: Exception) {
                        // Skip this entry
                    }
                }
                
                if (newCompressedCount > 0 && totalSpaceSaved > 0) {
                    NotificationManager.showPhotoCompressionCompleteNotification(
                        this@MonitoringService,
                        newCompressedCount,
                        totalSpaceSaved
                    )
                    
                    // Update last notified count
                    prefs.edit().putInt("last_notified_compressed_count", compressedUris.size).apply()
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun getDirectorySize(directory: java.io.File): Long {
        var size = 0L
        try {
            val files = directory.listFiles()
            files?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        } catch (e: Exception) {
            // Handle error
        }
        return size
    }

    private fun stopMonitoring() {
        isMonitoring = false
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
    }
}

