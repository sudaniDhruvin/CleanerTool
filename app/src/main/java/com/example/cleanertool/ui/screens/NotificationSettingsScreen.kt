package com.example.cleanertool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.example.cleanertool.utils.SettingsPreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPreferencesManager(context) }
    
    // Reminder states - load from preferences
    var junkReminderEnabled by remember { 
        mutableStateOf(settingsPrefs.getJunkReminder()) 
    }
    var ramReminderEnabled by remember { 
        mutableStateOf(settingsPrefs.getRamReminder()) 
    }
    var batteryReminderEnabled by remember { 
        mutableStateOf(settingsPrefs.getBatteryReminderNotification()) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notification Settings",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
        ) {
            // Junk Reminder
            NotificationReminderItem(
                icon = Icons.Default.Delete,
                title = "Junk Reminder",
                description = "Remind when overfull junk files",
                checked = junkReminderEnabled,
                onCheckedChange = { 
                    junkReminderEnabled = it
                    settingsPrefs.setJunkReminder(it)
                }
            )
            
            // RAM Reminder
            NotificationReminderItem(
                icon = Icons.Default.Refresh,
                title = "RAM Reminder",
                description = "Remind when RAM is too high",
                checked = ramReminderEnabled,
                onCheckedChange = { 
                    ramReminderEnabled = it
                    settingsPrefs.setRamReminder(it)
                }
            )
            
            // Battery Reminder
            NotificationReminderItem(
                icon = Icons.Default.Warning,
                title = "Battery Reminder",
                description = "Remind of battery charging status",
                checked = batteryReminderEnabled,
                onCheckedChange = { 
                    batteryReminderEnabled = it
                    settingsPrefs.setBatteryReminderNotification(it)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun NotificationReminderItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon and text
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Icon with gray background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color(0xFF424242),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF757575)
                    )
                }
            }
            
            // Toggle switch
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2196F3),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                )
            )
        }
    }
}

