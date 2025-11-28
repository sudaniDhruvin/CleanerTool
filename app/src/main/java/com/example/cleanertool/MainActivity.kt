package com.example.cleanertool

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.cleanertool.navigation.NavGraph
import com.example.cleanertool.receivers.BatteryBroadcastReceiver
import com.example.cleanertool.receivers.UninstallReceiver
import com.example.cleanertool.services.MonitoringService
import com.example.cleanertool.ui.theme.CleanerToolTheme
import com.example.cleanertool.utils.NotificationManager

class MainActivity : ComponentActivity() {
    private lateinit var batteryReceiver: BatteryBroadcastReceiver
    private lateinit var uninstallReceiver: UninstallReceiver

    // Permission launcher for storage permissions
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.i("MainActivity", "Storage permissions granted")
        } else {
            Log.w("MainActivity", "Storage permissions denied: $permissions")
            // You can show a dialog explaining why permissions are needed
        }
    }

    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.i("MainActivity", "Returned from all-files access screen")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request storage permissions
        requestStoragePermissions()

        // Create notification channels
        NotificationManager.createNotificationChannels(this)

        // Register battery receiver
        batteryReceiver = BatteryBroadcastReceiver()
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, batteryFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, batteryFilter)
        }

        // Register uninstall receiver
        uninstallReceiver = UninstallReceiver()
        val uninstallFilter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uninstallReceiver, uninstallFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uninstallReceiver, uninstallFilter)
        }

        // Start monitoring service
        val serviceIntent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_START_MONITORING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        enableEdgeToEdge()
        setContent {
            CleanerToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }

    private fun requestStoragePermissions() {
        val permissions = when {
            // Android 13+ (API 33+) - Granular media permissions
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }
            // Android 10-12 (API 29-32) - READ_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            // Android 9 and below (API 28-) - Both permissions
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }

        // Check which permissions are not yet granted
        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            Log.i("MainActivity", "Requesting permissions: ${notGrantedPermissions.joinToString()}")
            storagePermissionLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            Log.i("MainActivity", "All storage permissions already granted")
        }

        requestAllFilesAccessPermissionIfNeeded()
    }

    // Helper function to check if storage permissions are granted
    fun hasStoragePermissions(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                Environment.isExternalStorageManager() ||
                        (
                                ContextCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.READ_MEDIA_IMAGES
                                ) == PackageManager.PERMISSION_GRANTED &&
                                        ContextCompat.checkSelfPermission(
                                            this,
                                            Manifest.permission.READ_MEDIA_VIDEO
                                        ) == PackageManager.PERMISSION_GRANTED &&
                                        ContextCompat.checkSelfPermission(
                                            this,
                                            Manifest.permission.READ_MEDIA_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun requestAllFilesAccessPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStoragePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to launch app-specific all files intent: ${e.message}")
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStoragePermissionLauncher.launch(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to unregister battery receiver: ${e.message}")
        }
        try {
            unregisterReceiver(uninstallReceiver)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to unregister uninstall receiver: ${e.message}")
        }

        // Stop monitoring service
        val serviceIntent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_STOP_MONITORING
        }
        stopService(serviceIntent)
    }
}