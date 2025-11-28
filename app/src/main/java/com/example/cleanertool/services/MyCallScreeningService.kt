package com.example.cleanertool.services

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.example.cleanertool.ui.screens.AfterCallDialogActivity
import com.example.cleanertool.utils.NotificationManager
import com.example.cleanertool.utils.OverlayPermission

class MyCallScreeningService : CallScreeningService() {

    private var currentCallState = Call.STATE_NEW
    private var currentNumber: String? = null

    override fun onScreenCall(callDetails: Call.Details) {
        // observe calls here
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)

        // Optional: call ended handling requires your own logic
    }


    fun onCallStateChanged(state: Int, details: Call.Details?) {
        if (currentCallState != Call.STATE_DISCONNECTED && state == Call.STATE_DISCONNECTED) {
            onCallEnded(currentNumber)
        }
        currentCallState = state
    }

    private fun onCallEnded(phoneNumber: String?) {
        Log.d("phoneNumber", "onCallEnded: ", phoneNumber as Throwable?)
        // Show notification
        NotificationManager.showAfterCallNotification(applicationContext, phoneNumber)

        // Optional: overlay popup if permission granted
        if (OverlayPermission.hasOverlayPermission(applicationContext)) {
            val intent = Intent(applicationContext, AfterCallDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("phoneNumber", phoneNumber)
            }
            applicationContext.startActivity(intent)
        }
    }

}
