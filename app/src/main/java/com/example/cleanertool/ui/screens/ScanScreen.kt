package com.example.cleanertool.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.viewmodel.FileType
import com.example.cleanertool.viewmodel.ScanViewModel
import kotlinx.coroutines.delay

private val JunkCategoryTypes = setOf(FileType.JUNK, FileType.CACHE)
private val DefaultSelectedTypes: Set<FileType> = FileType.values().toSet()

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
    val savedSelectedCategories by viewModel.selectedCategories.collectAsState()
    
    var selectedCategories by remember { mutableStateOf(DefaultSelectedTypes) }
    
    LaunchedEffect(savedSelectedCategories) {
        if (savedSelectedCategories.isNotEmpty()) {
            selectedCategories = savedSelectedCategories
        }
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
                    containerColor = Color(0xFFFF5722)
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
                            // Store selected categories in ViewModel for CleanScreen to access
                            viewModel.setSelectedCategories(selectedCategories)
                            navController.navigate("clean")
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
                            text = "CLEAN",
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
            // Orange/Red gradient background (top section)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        brush = Brush.linearGradient(
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Orange section - Size display and scanning status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF7043),
                                    Color(0xFFFF5722)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Large size display
                        Text(
                            text = viewModel.formatFileSize(totalSize),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Scanning text with animated dots
                        if (isScanning) {
                            ScanningText()
                        } else {
                            Text(
                                text = "Scan Complete",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Scanning path bar
                        if (isScanning && scanningPath.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = scanningPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                
                // White section - Category list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 16.dp)
                ) {
                    // Junk Files
                    CategoryItem(
                        icon = Icons.Default.Delete,
                        title = "Junk Files",
                        size = viewModel.getTotalSizeByCategories(JunkCategoryTypes),
                        isScanning = isScanning && (currentScanningCategory == FileType.JUNK || currentScanningCategory == FileType.CACHE),
                        isSelected = JunkCategoryTypes.all { selectedCategories.contains(it) },
                        onToggle = {
                            selectedCategories = if (JunkCategoryTypes.all { selectedCategories.contains(it) }) {
                                selectedCategories - JunkCategoryTypes
                            } else {
                                selectedCategories + JunkCategoryTypes
                            }
                        }
                    )
                    
                    // Obsolete APK files
                    CategoryItem(
                        icon = Icons.Default.Phone,
                        title = "Obsolete APK files",
                        size = viewModel.getTotalSizeByCategory(FileType.OBSOLETE_APK),
                        isScanning = isScanning && currentScanningCategory == FileType.OBSOLETE_APK,
                        isSelected = selectedCategories.contains(FileType.OBSOLETE_APK),
                        onToggle = {
                            selectedCategories = if (selectedCategories.contains(FileType.OBSOLETE_APK)) {
                                selectedCategories - FileType.OBSOLETE_APK
                            } else {
                                selectedCategories + FileType.OBSOLETE_APK
                            }
                        }
                    )
                    
                    // Temp Files
                    CategoryItem(
                        icon = Icons.Default.Settings,
                        title = "Temp Files",
                        size = viewModel.getTotalSizeByCategory(FileType.TEMP),
                        isScanning = isScanning && currentScanningCategory == FileType.TEMP,
                        isSelected = selectedCategories.contains(FileType.TEMP),
                        onToggle = {
                            selectedCategories = if (selectedCategories.contains(FileType.TEMP)) {
                                selectedCategories - FileType.TEMP
                            } else {
                                selectedCategories + FileType.TEMP
                            }
                        }
                    )
                    
                    // Log Files
                    CategoryItem(
                        icon = Icons.Default.Info,
                        title = "Log Files",
                        size = viewModel.getTotalSizeByCategory(FileType.LOG),
                        isScanning = isScanning && currentScanningCategory == FileType.LOG,
                        isSelected = selectedCategories.contains(FileType.LOG),
                        onToggle = {
                            selectedCategories = if (selectedCategories.contains(FileType.LOG)) {
                                selectedCategories - FileType.LOG
                            } else {
                                selectedCategories + FileType.LOG
                            }
                        }
                    )
                    
                    // Banner Ad
                    Spacer(modifier = Modifier.height(16.dp))
                    BannerAdView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                    
                    // Add bottom padding to prevent content from being cut off by button
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

@Composable
fun ScanningText() {
    var dotCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = (dotCount + 1) % 4
        }
    }
    
    Text(
        text = "Scanning${".".repeat(dotCount)}",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.9f)
    )
}

@Composable
fun CategoryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    size: Long,
    isScanning: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
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
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Size or status
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF2196F3)
                )
            } else {
                Text(
                    text = formatSizeInKb(size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                // Checkbox
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF2196F3),
                        uncheckedColor = Color.Gray
                    )
                )
            }
        }
    }
}

private fun formatSizeInKb(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    return String.format("%.2f KB", kb)
}
