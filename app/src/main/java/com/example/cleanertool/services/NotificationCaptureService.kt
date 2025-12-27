package com.example.cleanertool.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.cleanertool.utils.NotificationStore
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class NotificationCaptureService : NotificationListenerService() {
    // log tag
    private val TAG = "NotificationCaptureSvc"

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val active = activeNotifications ?: arrayOf()
            NotificationStore.updateFromStatusBar(*active)
            Log.d(TAG, "Listener connected, ${active.size} active notifications")
            NotificationStore.setListenerConnected(true)

            // Register receiver so UI can request a clear-all via broadcast
            try {
                val filter = IntentFilter(ACTION_CLEAR_ALL)
                registerReceiver(clearReceiver, filter)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to register clear receiver", t)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize notifications list", t)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        NotificationStore.addNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        NotificationStore.removeNotification(sbn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationStore.setListenerConnected(false)
        try {
            unregisterReceiver(clearReceiver)
        } catch (_: Throwable) {
        }
    }

    private val clearReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CLEAR_ALL) {
                try {
                    // Cancel all notifications the listener can cancel
                    cancelAllNotifications()
                    // Clear the store optimistically
                    NotificationStore.clearAll()
                    // Show a short toast on main thread indicating action taken
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Clear requested: notifications removed if listener active.", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to clear notifications", t)
                }
            }
        }
    }

    companion object {
        const val ACTION_CLEAR_ALL = "com.example.cleanertool.ACTION_CLEAR_ALL"
        private const val LOG_TAG = "NotificationCaptureSvc"
    }

    /** Called by UI via NotificationStore when user requests a clear-all. */
    fun clearAllNotifications() {
        try {
            // Cancel notifications by key
            val items = activeNotifications ?: arrayOf()
            for (s in items) {
                try {
                    cancelNotification(s.key)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to cancel ${s.key}", t)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "clearAllNotifications failed", t)
        }
    }
}
