package com.example.cleanertool.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.viewmodel.FileType
import com.example.cleanertool.viewmodel.ScanViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: ScanViewModel = viewModel()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val unnecessaryFiles by viewModel.unnecessaryFiles.collectAsState()
    val totalSize by viewModel.totalSize.collectAsState()
    val error by viewModel.error.collectAsState()
    val scanningPath by viewModel.scanningPath.collectAsState()
    val currentScanningCategory by viewModel.currentScanningCategory.collectAsState()
    val filesByCategory by viewModel.filesByCategory.collectAsState()
    
    var selectedCategories by remember {
        mutableStateOf(setOf(FileType.JUNK, FileType.OBSOLETE_APK, FileType.TEMP, FileType.LOG))
    }

    // Start scanning when screen loads
    LaunchedEffect(Unit) {
        if (!isScanning && unnecessaryFiles.isEmpty()) {
            viewModel.scanDevice(context)
        }
    }

    // Calculate total size of selected categories
    val selectedTotalSize = remember(selectedCategories, filesByCategory) {
        selectedCategories.sumOf { category ->
            viewModel.getTotalSizeByCategory(category)
        }
    }

    // Validate if any files are selected
    val hasSelectedFiles = selectedTotalSize > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Junk Cleaner",
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
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            if (!isScanning) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Button(
                        onClick = {
                            // Pass selected categories to clean screen via navigation
                            navController.navigate("clean") {
                                popUpTo("scan") { inclusive = true }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasSelectedFiles) Color(0xFF64B5F6) else Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = hasSelectedFiles && error == null
                    ) {
                        Text(
                            text = if (hasSelectedFiles) {
                                "CLEAN ${viewModel.formatFileSize(selectedTotalSize)}"
                            } else {
                                "NO FILES TO CLEAN"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Orange-red gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF7043),
                                Color(0xFFFF5722)
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = if (isScanning) {
                            viewModel.formatFileSize(totalSize)
                        } else {
                            viewModel.formatFileSize(selectedTotalSize)
                        },
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isScanning) {
                        Text(
                            text = "Scanning...",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        
                        if (scanningPath.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE64A19)
                            ) {
                                Text(
                                    text = scanningPath,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    } else if (error != null) {
                        Text(
                            text = "Scan Failed",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFFFEBEE)
                        )
                    } else {
                        Text(
                            text = if (hasSelectedFiles) "Scan Complete" else "No Files Found",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Category list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    val categories = listOf(
                        Triple(FileType.JUNK, "Junk Files", Icons.Default.Delete),
                        Triple(FileType.OBSOLETE_APK, "Obsolete APK files", Icons.Default.Phone),
                        Triple(FileType.TEMP, "Temp Files", Icons.Default.Settings),
                        Triple(FileType.LOG, "Log Files", Icons.Default.Info)
                    )
                    
                    categories.forEach { (type, title, icon) ->
                        val fileSize = viewModel.getTotalSizeByCategory(type)
                        val isCurrentlyScanning = isScanning && currentScanningCategory == type
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = viewModel.formatFileSize(fileSize),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                
                                if (isCurrentlyScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2196F3)
                                    )
                                } else {
                                    Checkbox(
                                        checked = selectedCategories.contains(type),
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                selectedCategories = selectedCategories + type
                                            } else {
                                                selectedCategories = selectedCategories - type
                                            }
                                        },
                                        enabled = !isScanning && fileSize > 0
                                    )
                                }
                            }
                        }
                        
                        HorizontalDivider()
                    }
                    
                    // Show error if any
                    if (error != null && !isScanning) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F)
                                    )
                                    Text(
                                        text = error ?: "Unknown error occurred",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFD32F2F),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        viewModel.scanDevice(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD32F2F)
                                    )
                                ) {
                                    Text("Retry Scan")
                                }
                            }
                        }
                    }
                    
                    // Show empty state if scan complete but no files found
                    if (!isScanning && error == null && unnecessaryFiles.isEmpty()) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No junk files found!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your device is clean",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

