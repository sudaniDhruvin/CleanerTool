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
            while (isMonitoring) {
                checkJunkFiles()
                checkRamUsage()
                delay(5 * 60 * 1000) // Check every 5 minutes
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

