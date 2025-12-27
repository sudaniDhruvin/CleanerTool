package com.example.cleanertool.utils

import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimpleNotification(
    val key: String,
    val pkg: String,
    val title: String?,
    val text: String?
)

object NotificationStore {
    private val _notifications = MutableStateFlow<List<SimpleNotification>>(emptyList())
    val notifications = _notifications.asStateFlow()
    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected = _listenerConnected.asStateFlow()

    internal fun updateFromStatusBar(vararg sbs: StatusBarNotification) {
        val list = sbs.mapNotNull { sb ->
            val n = sb.notification
            val extras = n.extras
            SimpleNotification(
                key = sb.key,
                pkg = sb.packageName,
                title = extras.getString(android.app.Notification.EXTRA_TITLE),
                text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            )
        }
        // Ensure unique keys (some vendors may emit duplicate posted events)
        _notifications.value = list.distinctBy { it.key }
        // mark connected when we update from status bar
        _listenerConnected.value = true
    }

    internal fun addNotification(sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras
        val s = SimpleNotification(
            key = sbn.key,
            pkg = sbn.packageName,
            title = extras.getString(android.app.Notification.EXTRA_TITLE),
            text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        )
        // Replace any existing notification with same key to avoid duplicates that crash LazyColumn
        val filtered = _notifications.value.filterNot { it.key == s.key }
        _notifications.value = filtered + s
    }

    internal fun removeNotification(sbn: StatusBarNotification) {
        _notifications.value = _notifications.value.filterNot { it.key == sbn.key }
    }

    internal fun setListenerConnected(v: Boolean) {
        _listenerConnected.value = v
    }

    internal fun clearAll() {
        _notifications.value = emptyList()
    }
}
