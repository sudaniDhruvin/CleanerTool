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
    val chargingStatus: String,
    val current: Int, // Current in microamperes (μA)
    val capacity: Long // Battery capacity in microampere-hours (μAh)
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
            
            // Get battery capacity and current using BatteryManager properties
            val batteryManager = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            
            // Get current (power) using BatteryManager properties (API 21+)
            val current = try {
                // Try to get current now first
                val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                if (currentNow != Integer.MIN_VALUE) {
                    currentNow
                } else {
                    // Fallback to average current
                    val currentAvg = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                    if (currentAvg != Integer.MIN_VALUE) {
                        currentAvg
                    } else {
                        0
                    }
                }
            } catch (e: Exception) {
                0
            }
            val capacity = try {
                // Try to get charge counter (remaining charge in microampere-hours)
                val chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (chargeCounter > 0 && batteryPct > 0) {
                    // Calculate full capacity: chargeCounter / (batteryPct / 100)
                    // This gives us the full battery capacity in microampere-hours (μAh)
                    (chargeCounter * 100 / batteryPct)
                } else {
                    // If charge counter is not available, try to read from energy counter
                    try {
                        val energyCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                        if (energyCounter > 0 && batteryPct > 0 && voltage > 0) {
                            // Energy counter is in nanowatt-hours, convert to μAh
                            // Energy (nWh) = Voltage (mV) * Charge (μAh) / 1000
                            // So Charge (μAh) = Energy (nWh) * 1000 / Voltage (mV)
                            val currentCharge = (energyCounter * 1000 / voltage)
                            (currentCharge * 100 / batteryPct)
                        } else {
                            0L
                        }
                    } catch (e2: Exception) {
                        0L
                    }
                }
            } catch (e: Exception) {
                0L
            }

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
                chargingStatus = chargingStatusString,
                current = current,
                capacity = capacity
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

