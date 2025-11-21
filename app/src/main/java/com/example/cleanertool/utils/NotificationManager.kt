package com.example.cleanertool.utils

import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.cleanertool.MainActivity
import com.example.cleanertool.R

object NotificationManager {
    private const val CHANNEL_ID_BATTERY = "battery_reminders"
    private const val CHANNEL_ID_JUNK = "junk_reminders"
    private const val CHANNEL_ID_RAM = "ram_reminders"
    private const val CHANNEL_ID_UNINSTALL = "uninstall_reminders"
    private const val CHANNEL_ID_GENERAL = "general_reminders"
    
    private const val CHANNEL_NAME_BATTERY = "Battery Reminders"
    private const val CHANNEL_NAME_JUNK = "Junk File Reminders"
    private const val CHANNEL_NAME_RAM = "RAM Reminders"
    private const val CHANNEL_NAME_UNINSTALL = "Uninstall Reminders"
    private const val CHANNEL_NAME_GENERAL = "General Reminders"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

            // Battery Reminder Channel
            val batteryChannel = NotificationChannel(
                CHANNEL_ID_BATTERY,
                CHANNEL_NAME_BATTERY,
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for battery charging status and low battery warnings"
            }
            notificationManager.createNotificationChannel(batteryChannel)

            // Junk File Reminder Channel
            val junkChannel = NotificationChannel(
                CHANNEL_ID_JUNK,
                CHANNEL_NAME_JUNK,
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when junk files are overfull"
            }
            notificationManager.createNotificationChannel(junkChannel)

            // RAM Reminder Channel
            val ramChannel = NotificationChannel(
                CHANNEL_ID_RAM,
                CHANNEL_NAME_RAM,
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when RAM usage is too high"
            }
            notificationManager.createNotificationChannel(ramChannel)

            // Uninstall Reminder Channel
            val uninstallChannel = NotificationChannel(
                CHANNEL_ID_UNINSTALL,
                CHANNEL_NAME_UNINSTALL,
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when apps are uninstalled"
            }
            notificationManager.createNotificationChannel(uninstallChannel)

            // General Reminder Channel
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                CHANNEL_NAME_GENERAL,
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General optimization reminders"
            }
            notificationManager.createNotificationChannel(generalChannel)
        }
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showBatteryChargingNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BATTERY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Battery Charging")
            .setContentText("Your device is now charging")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(1001, notification)
    }

    fun showBatteryFullNotification(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BATTERY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Battery Full")
            .setContentText("Your battery is fully charged. You can unplug your charger.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(1002, notification)
    }

    fun showLowBatteryNotification(context: Context, batteryLevel: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BATTERY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Low Battery")
            .setContentText("Battery level is at $batteryLevel%. Please charge your device.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(1003, notification)
    }

    fun showChargingReportNotification(context: Context, batteryLevel: Int, temperature: Float) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BATTERY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Battery Charging Report")
            .setContentText("Battery: $batteryLevel% | Temperature: ${temperature.toInt()}°C")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Battery Level: $batteryLevel%\nTemperature: ${temperature.toInt()}°C\nCharging Status: Active"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(1004, notification)
    }

    fun showJunkFileNotification(context: Context, junkSize: Long) {
        val sizeInMB = junkSize / (1024 * 1024)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_JUNK)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Junk Files Detected")
            .setContentText("You have ${sizeInMB}MB of junk files. Clean them now!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(2001, notification)
    }

    fun showHighRamNotification(context: Context, ramUsagePercent: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RAM)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("High RAM Usage")
            .setContentText("RAM usage is at $ramUsagePercent%. Consider cleaning background apps.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(3001, notification)
    }

    fun showUninstallNotification(context: Context, appName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UNINSTALL)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("App Uninstalled")
            .setContentText("$appName has been uninstalled from your device.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(4001, notification)
    }

    fun showOptimizationReminder(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Optimization Reminder")
            .setContentText("It's time to optimize your device for better performance.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getPendingIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        notificationManager.notify(5001, notification)
    }
}

