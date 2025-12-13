package com.example.cleanertool.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.ads.NativeAdView
import com.example.cleanertool.viewmodel.FileType
import com.example.cleanertool.viewmodel.ScanViewModel
import kotlinx.coroutines.delay

private val JunkCategoryTypes = setOf(FileType.JUNK, FileType.CACHE)
private val DefaultSelectedTypes: Set<FileType> = FileType.values().toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ScanViewModel = viewModel()
    val unnecessaryFiles by viewModel.unnecessaryFiles.collectAsState()
    val totalSize by viewModel.totalSize.collectAsState()
    val filesByCategory by viewModel.filesByCategory.collectAsState()
    val savedSelectedCategories by viewModel.selectedCategories.collectAsState()
    
    // Use saved categories from ViewModel, or default to all if empty
    var selectedCategories by remember {
        mutableStateOf(
            if (savedSelectedCategories.isNotEmpty()) savedSelectedCategories
            else DefaultSelectedTypes
        )
    }
    
    // Update when ViewModel categories change
    LaunchedEffect(savedSelectedCategories) {
        if (savedSelectedCategories.isNotEmpty()) {
            selectedCategories = savedSelectedCategories
        }
    }
    
    var isCleaning by remember { mutableStateOf(false) }
    var cleanProgress by remember { mutableStateOf(0) }
    var cleaningComplete by remember { mutableStateOf(false) }
    var cleaningError by remember { mutableStateOf<String?>(null) }

    // Calculate selected files and size
    val selectedFiles = remember(selectedCategories, filesByCategory) {
        selectedCategories.flatMap { category ->
            viewModel.getFilesByCategory(category)
        }
    }
    
    val selectedTotalSize = remember(selectedCategories, filesByCategory) {
        selectedCategories.sumOf { category ->
            viewModel.getTotalSizeByCategory(category)
        }
    }

    LaunchedEffect(isCleaning) {
        if (isCleaning && !cleaningComplete) {
            cleaningError = null
            try {
                viewModel.cleanFiles(context, selectedCategories) { progress ->
                    cleanProgress = progress
                }
                cleaningComplete = true
                // Navigate to RAM/Process screen after cleaning completes
                delay(500) // Small delay to show 100%
                navController.navigate("ram_process") {
                    popUpTo("clean") { inclusive = true }
                }
            } catch (e: Exception) {
                cleaningError = e.message ?: "Failed to clean files"
                cleaningComplete = true
                isCleaning = false
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
                            onClick = {
                                if (selectedCategories.isNotEmpty() && selectedTotalSize > 0) {
                                    isCleaning = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = selectedCategories.isNotEmpty() && selectedTotalSize > 0 && cleaningError == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedCategories.isNotEmpty() && selectedTotalSize > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Gray
                                }
                            )
                        ) {
                            Icon(Icons.Default.Delete, "Clean")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedTotalSize > 0) {
                                    "Clean ${viewModel.formatFileSize(selectedTotalSize)}"
                                } else {
                                    "No Files Selected"
                                },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                text = "Found ${selectedFiles.size} files to clean",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Total size: ${viewModel.formatFileSize(selectedTotalSize)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            if (cleaningError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD32F2F)
                        )
                        Text(
                            text = cleaningError ?: "Error occurred",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            }

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
                    
                    // Show selected categories with sizes
                    val categories = listOf(
                        "Junk Files" to JunkCategoryTypes,
                        "Obsolete APK files" to setOf(FileType.OBSOLETE_APK),
                        "Temp Files" to setOf(FileType.TEMP),
                        "Log Files" to setOf(FileType.LOG)
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        categories.forEach { (title, types) ->
                            val isSelected = types.all { selectedCategories.contains(it) }
                            if (isSelected) {
                                val categorySize = viewModel.getTotalSizeByCategories(types)
                                val categoryFiles = types.flatMap { viewModel.getFilesByCategory(it) }
                                
                                if (categorySize > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = true,
                                                onCheckedChange = {
                                                    selectedCategories = selectedCategories - types
                                                },
                                                enabled = !isCleaning
                                            )
                                            Column {
                                                Text(
                                                    title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    "${categoryFiles.size} files",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            viewModel.formatFileSize(categorySize),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (selectedCategories.isEmpty()) {
                            Text(
                                text = "No categories selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            
            // Native Ad
            Spacer(modifier = Modifier.height(16.dp))
            NativeAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            
            // Banner Ad
            Spacer(modifier = Modifier.height(16.dp))
            BannerAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
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

