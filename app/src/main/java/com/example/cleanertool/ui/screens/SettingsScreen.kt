package com.example.cleanertool.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.cleanertool.utils.PermissionUtils
import com.example.cleanertool.utils.SettingsPreferencesManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPreferencesManager(context) }
    
    // Battery Reminder states - load from preferences
    var chargingReminderEnabled by remember { 
        mutableStateOf(settingsPrefs.getChargingReminder()) 
    }
    var chargingReportReminderEnabled by remember { 
        mutableStateOf(settingsPrefs.getChargingReportReminder()) 
    }
    var lowBatteryReminderEnabled by remember { 
        mutableStateOf(settingsPrefs.getLowBatteryReminder()) 
    }
    
    // Uninstall Reminder state - load from preferences
    var uninstallReminderEnabled by remember { 
        mutableStateOf(settingsPrefs.getUninstallReminder()) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
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
                .verticalScroll(rememberScrollState())
                .background(Color.White)
        ) {
            // Battery Reminder Section
            SettingsSection(title = "Battery Reminder") {
                SettingsSwitchItem(
                    icon = Icons.Default.Settings,
                    title = "Charging Reminder",
                    description = "Remind of battery charging status",
                    checked = chargingReminderEnabled,
                    onCheckedChange = { 
                        chargingReminderEnabled = it
                        settingsPrefs.setChargingReminder(it)
                    }
                )
                
                SettingsSwitchItem(
                    icon = Icons.Default.Info,
                    title = "Charging Report Reminder",
                    description = "Show charging details report",
                    checked = chargingReportReminderEnabled,
                    onCheckedChange = { 
                        chargingReportReminderEnabled = it
                        settingsPrefs.setChargingReportReminder(it)
                    }
                )
                
                SettingsSwitchItem(
                    icon = Icons.Default.Warning,
                    title = "Low Battery Reminder",
                    description = "Remind battery is low",
                    checked = lowBatteryReminderEnabled,
                    onCheckedChange = { 
                        lowBatteryReminderEnabled = it
                        settingsPrefs.setLowBatteryReminder(it)
                    }
                )
            }
            
            // Uninstall Reminder Section
            SettingsSection(title = "Uninstall Reminder") {
                SettingsSwitchItem(
                    icon = Icons.Default.Delete,
                    title = "Uninstall Reminder",
                    description = "Remind when apps uninstalled",
                    checked = uninstallReminderEnabled,
                    onCheckedChange = { 
                        uninstallReminderEnabled = it
                        settingsPrefs.setUninstallReminder(it)
                    }
                )
            }
            
            // Notification Reminder Section
            SettingsSection(title = "Notification Reminder") {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification Reminder",
                    description = "Remind optimization information",
                    onClick = {
                        navController.navigate("notification_settings")
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = Color.Gray
        )
    }
    HorizontalDivider()
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
    HorizontalDivider()
}

@Composable
fun PermissionSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFF2196F3),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = Color(0xFF4CAF50)
            )
        } else {
            TextButton(onClick = onRequest) {
                Text("Grant")
            }
        }
    }
    HorizontalDivider()
}

