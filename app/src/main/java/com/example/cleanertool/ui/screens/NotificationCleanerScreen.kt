package com.example.cleanertool.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.core.app.NotificationManagerCompat
import com.example.cleanertool.utils.NotificationStore
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCleanerScreen(navController: NavController) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    val notifications by NotificationStore.notifications.collectAsState()
    val listenerConnected by NotificationStore.listenerConnected.collectAsState()

    LaunchedEffect(Unit) {
        enabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        // Listen for changes
        // no direct listener; UI refreshes on recomposition and via service updates
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Cleaner", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)) {
            if (!enabled) {
                Text("Notification access is not enabled. Grant permission to view and clear all notifications.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    } catch (e: Exception) {
                        // ignore
                    }
                }) { Text("Grant access") }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Active notifications: ${notifications.size}")
                    Row {
                                Button(onClick = {
                                    if (!enabled) {
                                        android.widget.Toast.makeText(context, "Enable notification access first", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    // Confirm a destructive action
                                    androidx.activity.compose.LocalActivityResultRegistryOwner
                                    // Send broadcast to request listener to clear all notifications
                                    try {
                                        val b = Intent(com.example.cleanertool.services.NotificationCaptureService.ACTION_CLEAR_ALL)
                                        context.sendBroadcast(b)
                                        android.widget.Toast.makeText(context, if (listenerConnected) "Clear requested" else "Requested; listener not yet connected", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (t: Throwable) {
                                        android.widget.Toast.makeText(context, "Unable to request clear. Open notification access settings and try again.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Clean all") }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (notifications.isEmpty()) {
                    Text("No active notifications.")
                } else {
                    LazyColumn {
                        items(notifications, key = { it.key }) { n ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(n.pkg, fontWeight = FontWeight.Bold)
                                    n.title?.let { Text(it) }
                                    n.text?.let { Text(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
