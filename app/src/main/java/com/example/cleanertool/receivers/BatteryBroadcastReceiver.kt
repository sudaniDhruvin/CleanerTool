package com.example.cleanertool.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import com.example.cleanertool.utils.NotificationManager
import com.example.cleanertool.utils.SettingsPreferencesManager

class BatteryBroadcastReceiver : BroadcastReceiver() {
    private var lastChargingState = false
    private var lastBatteryLevel = -1
    private var lastNotificationTime = 0L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            val settingsPrefs = SettingsPreferencesManager(context)
            
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryLevel = (level * 100 / scale.toFloat()).toInt()
            
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val isFull = status == BatteryManager.BATTERY_STATUS_FULL
            
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f

            // Charging Reminder
            if (settingsPrefs.getChargingReminder()) {
                if (isCharging && !lastChargingState) {
                    // Just started charging
                    NotificationManager.showBatteryChargingNotification(context)
                }
                if (isFull && batteryLevel >= 100) {
                    // Battery is full
                    NotificationManager.showBatteryFullNotification(context)
                }
            }

            // Charging Report Reminder (show every 30 minutes while charging)
            if (settingsPrefs.getChargingReportReminder() && isCharging) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastNotificationTime > 30 * 60 * 1000) { // 30 minutes
                    NotificationManager.showChargingReportNotification(context, batteryLevel, temperature)
                    lastNotificationTime = currentTime
                }
            }

            // Low Battery Reminder
            if (settingsPrefs.getLowBatteryReminder() && !isCharging) {
                if (batteryLevel <= 20 && lastBatteryLevel > 20) {
                    // Just dropped below 20%
                    NotificationManager.showLowBatteryNotification(context, batteryLevel)
                } else if (batteryLevel <= 15 && lastBatteryLevel > 15) {
                    // Just dropped below 15%
                    NotificationManager.showLowBatteryNotification(context, batteryLevel)
                } else if (batteryLevel <= 10 && lastBatteryLevel > 10) {
                    // Just dropped below 10%
                    NotificationManager.showLowBatteryNotification(context, batteryLevel)
                }
            }

            lastChargingState = isCharging
            lastBatteryLevel = batteryLevel
        }
    }
}

