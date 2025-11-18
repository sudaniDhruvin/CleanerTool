package com.example.cleanertool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cleanertool.navigation.Screen

data class Feature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val color: androidx.compose.ui.graphics.Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val features = listOf(
        Feature(
            title = "Storage & Gallery",
            description = "Scan and manage your photos",
            icon = Icons.Default.Star,
            route = Screen.StorageGallery.route,
            color = MaterialTheme.colorScheme.primary
        ),
        Feature(
            title = "Battery & Charging",
            description = "Monitor battery health",
            icon = Icons.Default.Settings,
            route = Screen.BatteryCharging.route,
            color = MaterialTheme.colorScheme.secondary
        ),
        Feature(
            title = "App Management",
            description = "Manage installed apps",
            icon = Icons.Default.Menu,
            route = Screen.AppManagement.route,
            color = MaterialTheme.colorScheme.tertiary
        ),
        Feature(
            title = "Speaker Maintenance",
            description = "Clean and test speakers",
            icon = Icons.Default.Info,
            route = Screen.SpeakerMaintenance.route,
            color = MaterialTheme.colorScheme.error
        ),
        Feature(
            title = "Uninstall Reminder",
            description = "Find unused apps",
            icon = Icons.Default.Delete,
            route = Screen.UninstallReminder.route,
            color = MaterialTheme.colorScheme.primaryContainer
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cleaner Toolbox", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Clean & Optimize Your Device",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "All-in-one toolbox for device maintenance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(features) { feature ->
                    FeatureCard(
                        feature = feature,
                        onClick = { navController.navigate(feature.route) }
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    feature: Feature,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = feature.color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = feature.title,
                modifier = Modifier.size(48.dp),
                tint = feature.color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

