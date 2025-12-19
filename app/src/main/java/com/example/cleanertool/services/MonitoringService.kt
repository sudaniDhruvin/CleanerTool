package com.example.cleanertool.services

import android.app.*
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.example.cleanertool.utils.NotificationManager
import com.example.cleanertool.utils.OverlayPermission
import com.example.cleanertool.utils.RamUtils
import com.example.cleanertool.utils.SettingsPreferencesManager
import com.example.cleanertool.utils.StorageUtils
import com.example.cleanertool.utils.CallDirection
import com.example.cleanertool.utils.CallDirection.*
import com.example.cleanertool.utils.ContactUtils
import android.Manifest
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telecom.TelecomManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitoring = false

    companion object {
        private const val TAG = "MonitoringService"
        private const val CHANNEL_ID = "monitoring_service_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START_MONITORING = "com.example.cleanertool.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.cleanertool.STOP_MONITORING"
    }

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var callListener: PhoneStateListener
    private lateinit var callInfoReceiver: BroadcastReceiver
    private lateinit var uninstallReceiver: BroadcastReceiver
    private var pendingNumberFromScreening: String? = null
    private var pendingDirectionFromScreening: CallDirection = UNKNOWN

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+) requires service type to be specified
                @Suppress("NewApi")
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                // Android 11 and below
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            // Try without service type as fallback (shouldn't happen but just in case)
            try {
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to start foreground service even without type", e2)
            }
        }

        // Initialize telephony manager (only if permission is granted)
        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            // Only setup call listener if READ_PHONE_STATE permission is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
                == PackageManager.PERMISSION_GRANTED) {
                setupCallListener()
            } else {
                Log.w(TAG, "READ_PHONE_STATE permission not granted, skipping call listener setup")
            }
            
            registerCallInfoReceiver()
            registerUninstallReceiver()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing telephony manager or listeners", e)
            // Continue without call monitoring if there's an error
        }
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

    @SuppressLint("MissingPermission")
    private fun setupCallListener() {
        // Check permission before setting up listener
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted, cannot setup call listener")
            return
        }
        
        // Use default constructor; callbacks are delivered on the main thread.
        callListener = object : PhoneStateListener() {
            private var lastState = TelephonyManager.CALL_STATE_IDLE
            private var activeNumber: String? = null
            private var activeDirection: CallDirection = UNKNOWN
            private var callInProgress = false

            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                Log.d(TAG, "Call state changed: state=$state, phoneNumber=$phoneNumber, pendingNumber=$pendingNumberFromScreening")
                
                val sanitizedNumber = phoneNumber?.takeIf { it.isNotBlank() }
                if (sanitizedNumber != null) {
                    activeNumber = sanitizedNumber
                    Log.d(TAG, "Set activeNumber from PhoneStateListener: $activeNumber")
                } else if (activeNumber.isNullOrBlank() && !pendingNumberFromScreening.isNullOrBlank()) {
                    activeNumber = pendingNumberFromScreening
                    Log.d(TAG, "Set activeNumber from CallScreeningService: $activeNumber")
                }

                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        activeDirection = INCOMING
                        callInProgress = false
                        Log.d(TAG, "Call ringing - incoming call")
                    }

                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        activeDirection = when (lastState) {
                            TelephonyManager.CALL_STATE_RINGING -> INCOMING
                            else -> pendingDirectionFromScreening.takeUnless { it == UNKNOWN } ?: OUTGOING
                        }
                        if (activeNumber.isNullOrBlank()) {
                            activeNumber = pendingNumberFromScreening
                            Log.d(TAG, "Call offhook - using pendingNumber: $activeNumber")
                        }
                        callInProgress = true
                        Log.d(TAG, "Call offhook - direction: $activeDirection, number: $activeNumber")
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (callInProgress || lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                            Log.d(TAG, "Call ended - activeNumber: $activeNumber, direction: $activeDirection")
                            notifyCallEnded(activeNumber, activeDirection)
                        }
                        callInProgress = false
                        activeNumber = null
                        activeDirection = UNKNOWN
                        clearPendingCallInfo()
                    }
                }
                lastState = state
            }
        }

        try {
            telephonyManager.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to register call listener - permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register call listener", e)
        }
    }

    private fun registerCallInfoReceiver() {
        callInfoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != MyCallScreeningService.ACTION_CALL_IDENTIFIED) return
                pendingNumberFromScreening = intent.getStringExtra(MyCallScreeningService.EXTRA_PHONE_NUMBER)
                pendingDirectionFromScreening = intent
                    .getStringExtra(MyCallScreeningService.EXTRA_CALL_DIRECTION)
                    ?.let { runCatching { CallDirection.valueOf(it) }.getOrDefault(UNKNOWN) }
                    ?: UNKNOWN
                Log.d(TAG, "Received call info broadcast - Number: $pendingNumberFromScreening, Direction: $pendingDirectionFromScreening")
            }
        }

        val filter = IntentFilter(MyCallScreeningService.ACTION_CALL_IDENTIFIED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callInfoReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callInfoReceiver, filter)
        }
    }

    private fun registerUninstallReceiver() {
        uninstallReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_PACKAGE_REMOVED) return
                
                val settingsPrefs = SettingsPreferencesManager(this@MonitoringService)
                if (!settingsPrefs.getUninstallReminder()) return

                val packageName = intent.data?.schemeSpecificPart
                if (packageName != null && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    // App was uninstalled (not replaced)
                    try {
                        val packageManager = context?.packageManager ?: return
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        
                        NotificationManager.showUninstallNotification(this@MonitoringService, appName)
                        maybeShowUninstallOverlay(appName)
                    } catch (e: Exception) {
                        // App info not available, use package name
                        NotificationManager.showUninstallNotification(this@MonitoringService, packageName)
                        maybeShowUninstallOverlay(packageName)
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uninstallReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uninstallReceiver, filter)
        }
    }

    private fun maybeShowUninstallOverlay(appName: String) {
        Log.d(TAG, "App uninstalled: $appName")
        if (!OverlayPermission.hasOverlayPermission(this)) {
            Log.w(TAG, "Overlay permission not granted. Requesting permission...")
            // Automatically open overlay permission settings
            OverlayPermission.requestOverlayPermission(this)
            // Show a notification to guide user
            NotificationManager.showOverlayPermissionNotification(this)
            return
        }

        val overlayIntent = Intent(this, CallOverlayService::class.java).apply {
            putExtra(CallOverlayService.EXTRA_PRIMARY_TEXT, appName)
            putExtra(CallOverlayService.EXTRA_SECONDARY_TEXT, "Tap to clean leftovers")
            putExtra(CallOverlayService.EXTRA_MODE, CallOverlayService.MODE_UNINSTALL)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
            Log.d(TAG, "Started CallOverlayService for uninstall")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CallOverlayService for uninstall", e)
        }
    }

    private fun clearPendingCallInfo() {
        pendingNumberFromScreening = null
        pendingDirectionFromScreening = UNKNOWN
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

    private fun notifyCallEnded(phoneNumber: String?, callDirection: CallDirection) {
        Log.d(TAG, "Call ended: $phoneNumber, direction: ${callDirection.name}")

        // If phone number is null or empty, try to get it from CallLog with retries (and show overlay when found)
        if (phoneNumber.isNullOrBlank()) {
            Log.d(TAG, "Phone number is null/empty, will try to get from CallLog with retries")
            tryGetNumberFromCallLogWithRetries(callDirection, retryCount = 0)
            return
        }

        // Phone number is available; attempt to read duration immediately. If a final duration (>0)
        // is found, show overlay with it. If not, show overlay now and schedule retries that will
        // continue until a positive duration is found (or retries exhausted).
        val details = getLastCallDetails()
        if (details != null && !details.number.isNullOrBlank() && numbersMatch(details.number, phoneNumber) && details.duration > 0) {
            Log.d(TAG, "Found immediate call details for $phoneNumber (duration=${details.duration})")
            showCallOverlay(phoneNumber, callDirection, details.duration)
        } else {
            Log.d(TAG, "No immediate final duration for $phoneNumber, showing overlay and scheduling duration retry")
            // Show overlay immediately without duration
            showCallOverlay(phoneNumber, callDirection, 0)
            // Schedule retries to update duration when available; don't show a fallback overlay on failure
            tryGetNumberFromCallLogWithRetries(callDirection, retryCount = 0, targetNumber = phoneNumber, showOnFailure = false)
        }
    }
    
    private fun tryGetNumberFromCallLogWithRetries(callDirection: CallDirection, retryCount: Int, targetNumber: String? = null, showOnFailure: Boolean = true) {
        // Increase retry attempts and delays to allow CallLog to be populated with final duration
        val maxRetries = 8
        val delays = arrayOf(500L, 1000L, 1500L, 2000L, 3000L, 5000L, 7000L, 10000L) // Increasing delays up to 10s
        
        if (retryCount >= maxRetries) {
            Log.w(TAG, "Failed to get number from CallLog after $maxRetries retries")
            if (showOnFailure) showCallOverlay(null, callDirection)
            return
        }
        
        val delay = if (retryCount < delays.size) delays[retryCount] else delays.last()
        
        Handler(Looper.getMainLooper()).postDelayed({
            val details = getLastCallDetails()
            if (details != null && !details.number.isNullOrBlank()) {
                Log.d(TAG, "Got number from CallLog on retry $retryCount: ${details.number} (duration: ${details.duration})")
                // If a target number was provided, ensure it matches before updating
                if (targetNumber == null || numbersMatch(details.number, targetNumber)) {
                    // Only accept a positive duration as final; if duration is zero, keep retrying
                    if (details.duration > 0) {
                        Log.d(TAG, "Final duration available for ${details.number}: ${details.duration}s, updating overlay")
                        showCallOverlay(details.number, callDirection, details.duration)
                    } else {
                        Log.d(TAG, "Duration is still zero for ${details.number} on retry $retryCount; will retry...")
                        tryGetNumberFromCallLogWithRetries(callDirection, retryCount + 1, targetNumber, showOnFailure)
                    }
                } else {
                    Log.d(TAG, "Found details for ${details.number} but waiting for target $targetNumber; retrying...")
                    tryGetNumberFromCallLogWithRetries(callDirection, retryCount + 1, targetNumber, showOnFailure)
                }
            } else {
                Log.d(TAG, "CallLog read returned null on retry $retryCount, retrying...")
                tryGetNumberFromCallLogWithRetries(callDirection, retryCount + 1, targetNumber, showOnFailure)
            }
        }, delay)
    }
    
    private fun showCallOverlay(phoneNumber: String?, callDirection: CallDirection, durationSeconds: Int = 0) {
        Log.d(TAG, "Showing call overlay for number: $phoneNumber")
        
        // Get contact name from phone number
        val (contactName, displayNumber) = ContactUtils.getContactNameFromNumber(this, phoneNumber)
        Log.d(TAG, "Contact lookup - Name: $contactName, Number: $displayNumber")
        
        NotificationManager.showAfterCallNotification(this, displayNumber, callDirection)

        if (!OverlayPermission.hasOverlayPermission(this)) {
            Log.w(TAG, "Overlay permission not granted. Requesting permission...")
            // Automatically open overlay permission settings
            OverlayPermission.requestOverlayPermission(this)
            // Show a notification to guide user
            NotificationManager.showOverlayPermissionNotification(this)
            return
        }

        // Show lightweight overlay widget via background service
        val overlayIntent = Intent(this, CallOverlayService::class.java).apply {
            // Pass contact name as primary text, number as secondary
            putExtra(CallOverlayService.EXTRA_CONTACT_NAME, contactName)
            putExtra(CallOverlayService.EXTRA_PHONE_NUMBER, displayNumber)
            putExtra(CallOverlayService.EXTRA_CALL_DIRECTION, callDirection.name)
            // Include call duration (seconds) if available so overlay can show it in the banner
            putExtra(CallOverlayService.EXTRA_CALL_DURATION_SECONDS, durationSeconds)
            // Provide the approximate call end timestamp so overlay can show elapsed time
            putExtra(CallOverlayService.EXTRA_CALL_END_TIMESTAMP, System.currentTimeMillis())
            putExtra(CallOverlayService.EXTRA_MODE, CallOverlayService.MODE_CALL)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
            Log.d(TAG, "Started CallOverlayService for call ended")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CallOverlayService", e)
        }
    }
    
    /**
     * Get the phone number from the most recent call in CallLog as a fallback
     */
    private fun getLastCallNumber(): String? {
        // Check if READ_CALL_LOG permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG permission not granted, cannot read CallLog")
            return null
        }
        
        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            
            // Strategy 1: Get the most recent call (within last 120 seconds)
            val currentTime = System.currentTimeMillis()
            val twoMinutesAgo = currentTime - 120000
            
            var selection = "${CallLog.Calls.DATE} > ?"
            var selectionArgs = arrayOf(twoMinutesAgo.toString())
            
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                    
                    if (numberIndex >= 0) {
                        val number = cursor.getString(numberIndex)
                        val callDate = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                        val duration = if (durationIndex >= 0) cursor.getInt(durationIndex) else 0
                        
                        // Verify this is a recent call (within last 2 minutes) and had some duration
                        if (!number.isNullOrBlank() && 
                            callDate > twoMinutesAgo && 
                            (duration > 0 || callDate > currentTime - 10000)) { // Allow calls that just ended
                            Log.d(TAG, "Got number from CallLog: $number (date: $callDate, duration: $duration)")
                            return number
                        }
                    }
                }
            }
            
            // Strategy 2: Get the absolute most recent call without time filter
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                    
                    if (numberIndex >= 0) {
                        val number = cursor.getString(numberIndex)
                        val callDate = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                        
                        // Check if this call was very recent (within last 5 minutes)
                        if (!number.isNullOrBlank() && callDate > currentTime - 300000) {
                            Log.d(TAG, "Got number from CallLog (no filter): $number")
                            return number
                        }
                    }
                }
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading CallLog - permission may have been revoked", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CallLog", e)
        }
        return null
    }

    private data class CallDetails(val number: String?, val date: Long, val duration: Int)

    /**
     * Compare two phone numbers in a tolerant way: first try normalized equality, then
     * fallback to matching on last 7 digits to tolerate formatting/country code differences.
     */
    private fun numbersMatch(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        try {
            val na = android.telephony.PhoneNumberUtils.normalizeNumber(a)
            val nb = android.telephony.PhoneNumberUtils.normalizeNumber(b)
            if (na == nb) return true
            // Fallback: compare last 7 digits
            val la = na.filter { it.isDigit() }
            val lb = nb.filter { it.isDigit() }
            val minLen = minOf(7, minOf(la.length, lb.length))
            return la.takeLast(minLen) == lb.takeLast(minLen)
        } catch (e: Exception) {
            Log.w(TAG, "Error normalizing phone numbers", e)
            return a == b
        }
    }

    /**
     * Read the most recent call details (number, date, duration) using the same strategy
     * as getLastCallNumber but returning duration as well.
     */
    private fun getLastCallDetails(): CallDetails? {
        // Check if READ_CALL_LOG permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG permission not granted, cannot read CallLog")
            return null
        }

        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            val currentTime = System.currentTimeMillis()
            val twoMinutesAgo = currentTime - 120000

            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} > ?",
                arrayOf(twoMinutesAgo.toString()),
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                    if (numberIndex >= 0) {
                        val number = cursor.getString(numberIndex)
                        val callDate = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                        val duration = if (durationIndex >= 0) cursor.getInt(durationIndex) else 0

                        if (!number.isNullOrBlank() && callDate > twoMinutesAgo &&
                            (duration > 0 || callDate > currentTime - 10000)) {
                            return CallDetails(number, callDate, duration)
                        }
                    }
                }
            }

            // Fallback: return most recent call within last 5 minutes
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                    if (numberIndex >= 0) {
                        val number = cursor.getString(numberIndex)
                        val callDate = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                        val duration = if (durationIndex >= 0) cursor.getInt(durationIndex) else 0

                        if (!number.isNullOrBlank() && callDate > currentTime - 300000) {
                            return CallDetails(number, callDate, duration)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CallLog for details", e)
        }

        return null
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Try to keep monitoring alive when the app task is swiped away.
        val restartIntent = Intent(applicationContext, MonitoringService::class.java).apply {
            action = ACTION_START_MONITORING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::telephonyManager.isInitialized && ::callListener.isInitialized) {
                telephonyManager.listen(callListener, PhoneStateListener.LISTEN_NONE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering call listener", e)
        }
        try {
            unregisterReceiver(callInfoReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(uninstallReceiver)
        } catch (_: Exception) {
        }
        stopMonitoring()
    }
}
