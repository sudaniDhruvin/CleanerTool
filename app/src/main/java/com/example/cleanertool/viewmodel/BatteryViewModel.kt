package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val health: String,
    val temperature: Float,
    val voltage: Int,
    val technology: String,
    val chargingStatus: String
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfo: StateFlow<BatteryInfo?> = _batteryInfo.asStateFlow()

    private val _lowBatteryReminderEnabled = MutableStateFlow(false)
    val lowBatteryReminderEnabled: StateFlow<Boolean> = _lowBatteryReminderEnabled.asStateFlow()

    private val _chargingReminderEnabled = MutableStateFlow(false)
    val chargingReminderEnabled: StateFlow<Boolean> = _chargingReminderEnabled.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryInfo(intent)
        }
    }

    init {
        registerBatteryReceiver()
        updateBatteryInfo(null)
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        getApplication<Application>().registerReceiver(batteryReceiver, filter)
    }

    private fun updateBatteryInfo(intent: Intent?) {
        val batteryIntent = intent ?: getApplication<Application>().registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        batteryIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = (level * 100 / scale.toFloat()).toInt()

            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val health = it.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthString = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }

            val temperature = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
            val voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val technology = it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

            val chargingStatusString = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }

            _batteryInfo.value = BatteryInfo(
                level = batteryPct,
                isCharging = isCharging,
                health = healthString,
                temperature = temperature,
                voltage = voltage,
                technology = technology,
                chargingStatus = chargingStatusString
            )
        }
    }

    fun toggleLowBatteryReminder(enabled: Boolean) {
        _lowBatteryReminderEnabled.value = enabled
    }

    fun toggleChargingReminder(enabled: Boolean) {
        _chargingReminderEnabled.value = enabled
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}

