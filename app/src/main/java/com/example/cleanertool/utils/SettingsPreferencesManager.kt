package com.example.cleanertool.utils

import android.content.Context
import android.content.SharedPreferences

class SettingsPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "CleanerToolboxSettings"
        
        // Battery Reminder keys
        private const val KEY_CHARGING_REMINDER = "charging_reminder"
        private const val KEY_CHARGING_REPORT_REMINDER = "charging_report_reminder"
        private const val KEY_LOW_BATTERY_REMINDER = "low_battery_reminder"
        
        // Uninstall Reminder keys
        private const val KEY_UNINSTALL_REMINDER = "uninstall_reminder"
        
        // Notification Reminder keys
        private const val KEY_JUNK_REMINDER = "junk_reminder"
        private const val KEY_RAM_REMINDER = "ram_reminder"
        private const val KEY_BATTERY_REMINDER_NOTIFICATION = "battery_reminder_notification"
    }

    // Battery Reminder
    fun getChargingReminder(): Boolean = prefs.getBoolean(KEY_CHARGING_REMINDER, true)
    fun setChargingReminder(enabled: Boolean) = prefs.edit().putBoolean(KEY_CHARGING_REMINDER, enabled).apply()
    
    fun getChargingReportReminder(): Boolean = prefs.getBoolean(KEY_CHARGING_REPORT_REMINDER, true)
    fun setChargingReportReminder(enabled: Boolean) = prefs.edit().putBoolean(KEY_CHARGING_REPORT_REMINDER, enabled).apply()
    
    fun getLowBatteryReminder(): Boolean = prefs.getBoolean(KEY_LOW_BATTERY_REMINDER, true)
    fun setLowBatteryReminder(enabled: Boolean) = prefs.edit().putBoolean(KEY_LOW_BATTERY_REMINDER, enabled).apply()
    
    // Uninstall Reminder
    fun getUninstallReminder(): Boolean = prefs.getBoolean(KEY_UNINSTALL_REMINDER, true)
    fun setUninstallReminder(enabled: Boolean) = prefs.edit().putBoolean(KEY_UNINSTALL_REMINDER, enabled).apply()
    
    // Notification Reminder
    fun getJunkReminder(): Boolean = prefs.getBoolean(KEY_JUNK_REMINDER, true)
    fun setJunkReminder(enabled: Boolean) = prefs.edit().putBoolean(KEY_JUNK_REMINDER, enabled).apply()
    
    fun getRamReminder(): Boolean = prefs.getBoolean(KEY_RAM_REMINDER, true)
    fun setRamReminder(enabled: Boolean) = prefs.edit().putBoolean(KEY_RAM_REMINDER, enabled).apply()
    
    fun getBatteryReminderNotification(): Boolean = prefs.getBoolean(KEY_BATTERY_REMINDER_NOTIFICATION, true)
    fun setBatteryReminderNotification(enabled: Boolean) = prefs.edit().putBoolean(KEY_BATTERY_REMINDER_NOTIFICATION, enabled).apply()
}

