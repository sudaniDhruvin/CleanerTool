package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanertool.utils.RamUtils
import com.example.cleanertool.utils.UsageStatsUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RamViewModel(application: Application) : AndroidViewModel(application) {
    private val _ramInfo = MutableStateFlow<com.example.cleanertool.utils.RamInfo?>(null)
    val ramInfo: StateFlow<com.example.cleanertool.utils.RamInfo?> = _ramInfo.asStateFlow()

    private val _runningApps = MutableStateFlow<List<com.example.cleanertool.utils.RunningApp>>(emptyList())
    val runningApps: StateFlow<List<com.example.cleanertool.utils.RunningApp>> = _runningApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadRamInfo(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load RAM info
                val ramInfo = RamUtils.getRamInfo(context)
                _ramInfo.value = ramInfo

                // Get actually running background apps using ActivityManager
                // This shows apps that are currently running in background (like Truecaller, Slack, etc.)
                val runningApps = RamUtils.getRunningApps(context)
                _runningApps.value = runningApps
                
                android.util.Log.d("RamViewModel", "Loaded ${runningApps.size} running background apps")
            } catch (e: Exception) {
                android.util.Log.e("RamViewModel", "Error loading RAM info: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshApps(context: Context) {
        loadRamInfo(context)
    }
}

