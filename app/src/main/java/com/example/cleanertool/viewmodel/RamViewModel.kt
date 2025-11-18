package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanertool.utils.RamUtils
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
                val ramInfo = RamUtils.getRamInfo(context)
                _ramInfo.value = ramInfo

                val runningApps = RamUtils.getRunningApps(context)
                _runningApps.value = runningApps
            } catch (e: Exception) {
                // Handle error silently
            } finally {
                _isLoading.value = false
            }
        }
    }
}

