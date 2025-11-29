package com.example.cleanertool.services

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.example.cleanertool.utils.CallDirection
import com.example.cleanertool.ui.screens.AfterCallDialogActivity
import com.example.cleanertool.utils.NotificationManager
import com.example.cleanertool.utils.OverlayPermission

class MyCallScreeningService : CallScreeningService() {

    private var currentCallState = Call.STATE_NEW
    private var currentNumber: String? = null

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart
        val direction = when (callDetails.callDirection) {
            Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
            Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
            else -> CallDirection.UNKNOWN
        }

        broadcastCallIdentified(phoneNumber, direction)

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
        Log.d("MyCallScreeningService", "onCallEnded: $phoneNumber")
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

    private fun broadcastCallIdentified(phoneNumber: String?, direction: CallDirection) {
        val intent = Intent(ACTION_CALL_IDENTIFIED).apply {
            setPackage(packageName)
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(EXTRA_CALL_DIRECTION, direction.name)
        }
        applicationContext.sendBroadcast(intent)
    }

    companion object {
        const val ACTION_CALL_IDENTIFIED = "com.example.cleanertool.ACTION_CALL_IDENTIFIED"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_DIRECTION = "extra_call_direction"
    }
}
