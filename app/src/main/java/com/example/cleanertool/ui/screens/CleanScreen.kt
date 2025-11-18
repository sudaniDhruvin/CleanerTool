package com.example.cleanertool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.viewmodel.ScanViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ScanViewModel = viewModel()
    val unnecessaryFiles by viewModel.unnecessaryFiles.collectAsState()
    val totalSize by viewModel.totalSize.collectAsState()
    var isCleaning by remember { mutableStateOf(false) }
    var cleanProgress by remember { mutableStateOf(0) }
    var cleaningComplete by remember { mutableStateOf(false) }

    LaunchedEffect(isCleaning) {
        if (isCleaning && !cleaningComplete) {
            viewModel.cleanFiles(context) { progress ->
                cleanProgress = progress
            }
            cleaningComplete = true
            // Navigate to RAM/Process screen after cleaning completes
            delay(500) // Small delay to show 100%
            navController.navigate("ram_process") {
                popUpTo("clean") { inclusive = true }
            }
        }
    }
    
    // Show full screen loader when cleaning
    if (isCleaning && !cleaningComplete) {
        FullScreenLoader(progress = cleanProgress)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    if (isCleaning) {
                        // Show cleaning progress
                        Text(
                            text = "Cleaning... $cleanProgress%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { cleanProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Button(
                            onClick = { isCleaning = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Clean")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Clean",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Clean the Phone",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Found ${unnecessaryFiles.size} unnecessary files",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Total size: ${viewModel.formatFileSize(totalSize)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Files to clean:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Calculate sizes by type
                    val junkFiles = unnecessaryFiles.filter { it.type == com.example.cleanertool.viewmodel.FileType.JUNK }
                    val apkFiles = unnecessaryFiles.filter { it.type == com.example.cleanertool.viewmodel.FileType.OBSOLETE_APK }
                    val tempFiles = unnecessaryFiles.filter { it.type == com.example.cleanertool.viewmodel.FileType.TEMP }
                    val logFiles = unnecessaryFiles.filter { it.type == com.example.cleanertool.viewmodel.FileType.LOG }
                    
                    val junkSize = junkFiles.sumOf { it.size }
                    val apkSize = apkFiles.sumOf { it.size }
                    val tempSize = tempFiles.sumOf { it.size }
                    val logSize = logFiles.sumOf { it.size }
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (junkSize > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Junk file", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    viewModel.formatFileSize(junkSize),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (apkSize > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Obsolete APK", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    viewModel.formatFileSize(apkSize),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (tempSize > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Temp file", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    viewModel.formatFileSize(tempSize),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (logSize > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Log file", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    viewModel.formatFileSize(logSize),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenLoader(progress: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Cleaning... $progress%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

