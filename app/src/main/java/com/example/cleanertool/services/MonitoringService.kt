package com.example.cleanertool.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.example.cleanertool.ui.screens.AfterCallDialogActivity
import com.example.cleanertool.utils.NotificationManager
import com.example.cleanertool.utils.OverlayPermission
import com.example.cleanertool.utils.RamUtils
import com.example.cleanertool.utils.SettingsPreferencesManager
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

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var callListener: PhoneStateListener

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Initialize telephony manager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Setup call listener
        callListener = object : PhoneStateListener() {
            private var lastState = TelephonyManager.CALL_STATE_IDLE
            private var savedNumber: String? = null

            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> savedNumber = phoneNumber
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                            // Call ended
                            showAfterCallDialog(savedNumber)
                        }
                    }
                }
                lastState = state
            }
        }

        // Start listening for call state changes
        telephonyManager.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
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
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Monitoring Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background monitoring for device optimization"
            }
            val notificationManager =
                getSystemService(android.app.NotificationManager::class.java)
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
            var lastStorageNotification = 0L
            var lastRamNotification = 0L
            var lastJunkNotification = 0L
            var lastBatteryCheck = 0L

            while (isMonitoring) {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastStorageNotification > 10 * 60 * 1000) {
                    checkStorage()
                    lastStorageNotification = currentTime
                }

                if (currentTime - lastRamNotification > 5 * 60 * 1000) {
                    checkRamUsage()
                    lastRamNotification = currentTime
                }

                if (currentTime - lastJunkNotification > 15 * 60 * 1000) {
                    checkJunkFiles()
                    lastJunkNotification = currentTime
                }

                if (currentTime - lastBatteryCheck > 5 * 60 * 1000) {
                    checkBatteryStatus()
                    lastBatteryCheck = currentTime
                }

                checkPhotoCompressionStatus()
                delay(2 * 60 * 1000)
            }
        }
    }

    private fun showAfterCallDialog(phoneNumber: String?) {
        // Show notification as backup
        NotificationManager.showJunkFileNotification(this, 0)

        if (OverlayPermission.hasOverlayPermission(this)) {
            val intent = Intent(this, AfterCallDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("phoneNumber", phoneNumber)
            }
            startActivity(intent)
        }
    }

    private suspend fun checkJunkFiles() = withContext(Dispatchers.IO) {
        val settingsPrefs = SettingsPreferencesManager(this@MonitoringService)
        if (!settingsPrefs.getJunkReminder()) return@withContext

        val cacheSize = getDirectorySize(cacheDir)
        if (cacheSize > 100 * 1024 * 1024) {
            NotificationManager.showJunkFileNotification(this@MonitoringService, cacheSize)
        }
    }

    private suspend fun checkRamUsage() = withContext(Dispatchers.IO) {
        val settingsPrefs = SettingsPreferencesManager(this@MonitoringService)
        if (!settingsPrefs.getRamReminder()) return@withContext

        val ramInfo = RamUtils.getRamInfo(this@MonitoringService)
        if (ramInfo.ramUsagePercentage >= 80) {
            NotificationManager.showHighRamNotification(this@MonitoringService, ramInfo.ramUsagePercentage)
        }
    }

    private suspend fun checkStorage() = withContext(Dispatchers.IO) {
        val storageInfo = StorageUtils.getStorageInfo(this@MonitoringService)
        if (storageInfo.usagePercentage >= 85) {
            NotificationManager.showStorageFullNotification(this@MonitoringService, storageInfo.freeSpace, storageInfo.usagePercentage)
        }
    }

    private suspend fun checkBatteryStatus() = withContext(Dispatchers.IO) {
        val settingsPrefs = SettingsPreferencesManager(this@MonitoringService)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryLevel = (level * 100 / scale.toFloat()).toInt()
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isFull = status == BatteryManager.BATTERY_STATUS_FULL

            if (settingsPrefs.getChargingReminder() && isFull && batteryLevel >= 100) {
                NotificationManager.showBatteryFullNotification(this@MonitoringService)
            }

            if (settingsPrefs.getLowBatteryReminder() && batteryLevel <= 20) {
                NotificationManager.showLowBatteryNotification(this@MonitoringService, batteryLevel)
            }
        }
    }

    private suspend fun checkPhotoCompressionStatus() = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("compressed_images", Context.MODE_PRIVATE)
        val compressedUris = prefs.getStringSet("compressed_uris", emptySet()) ?: emptySet()
        val lastNotifiedCount = prefs.getInt("last_notified_compressed_count", 0)

        if (compressedUris.size > lastNotifiedCount) {
            val newCompressedCount = compressedUris.size - lastNotifiedCount
            var totalSpaceSaved = 0L

            compressedUris.forEach { uriString ->
                try {
                    val uri = android.net.Uri.parse(uriString)
                    val key = "size_${uri.toString().hashCode()}"
                    val originalSize = prefs.getLong("${key}_original", 0L)
                    val compressedSize = prefs.getLong("${key}_compressed", 0L)
                    if (originalSize > 0 && compressedSize > 0) totalSpaceSaved += (originalSize - compressedSize)
                } catch (_: Exception) {}
            }

            if (newCompressedCount > 0 && totalSpaceSaved > 0) {
                NotificationManager.showPhotoCompressionCompleteNotification(this@MonitoringService, newCompressedCount, totalSpaceSaved)
                prefs.edit().putInt("last_notified_compressed_count", compressedUris.size).apply()
            }
        }
    }

    private fun getDirectorySize(directory: java.io.File): Long {
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return size
    }

    private fun stopMonitoring() {
        isMonitoring = false
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonyManager.listen(callListener, PhoneStateListener.LISTEN_NONE)
        stopMonitoring()
    }
}
