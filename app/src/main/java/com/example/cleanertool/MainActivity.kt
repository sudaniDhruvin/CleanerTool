package com.example.cleanertool

import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create notification channels
        NotificationManager.createNotificationChannels(this)
        
        // Register battery receiver
        batteryReceiver = BatteryBroadcastReceiver()
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, batteryFilter)
        
        // Register uninstall receiver
        uninstallReceiver = UninstallReceiver()
        val uninstallFilter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
        }
        registerReceiver(uninstallReceiver, uninstallFilter)
        
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        try {
            unregisterReceiver(uninstallReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        
        // Stop monitoring service
        val serviceIntent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_STOP_MONITORING
        }
        stopService(serviceIntent)
    }
}