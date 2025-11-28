package com.example.cleanertool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateCleanerScreen(navController: NavController) {
    val context = LocalContext.current
    val scanViewModel: ScanViewModel = viewModel()
    val unnecessaryFiles by scanViewModel.unnecessaryFiles.collectAsState()
    val isScanning by scanViewModel.isScanning.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var cleaningProgress by remember { mutableIntStateOf(0) }
    var isCleaning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isScanning && unnecessaryFiles.isEmpty()) {
            scanViewModel.scanDevice(context)
        }
    }

    val duplicateGroups = remember(unnecessaryFiles) { scanViewModel.getDuplicateGroups() }
    val totalRecoverableBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { group ->
            group.drop(1).sumOf { it.size }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Duplicate Cleaner", fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF4CAF50))
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (duplicateGroups.isNotEmpty()) {
                Button(
                    onClick = {
                        isCleaning = true
                        scanViewModel.cleanDuplicateGroups(context) { progress ->
                            cleaningProgress = progress
                            if (progress >= 100) {
                                isCleaning = false
                                Toast.makeText(
                                    context,
                                    "Duplicates removed successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isCleaning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFFB2DFDB)
                    )
                ) {
                    if (isCleaning) {
                        Text("Cleaning... $cleaningProgress%", color = Color.White)
                    } else {
                        Text(
                            "Delete duplicate copies (${formatSize(totalRecoverableBytes)})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        when {
            isScanning -> LoadingState(paddingValues = paddingValues, message = "Scanning your storage…")
            duplicateGroups.isEmpty() -> EmptyState(paddingValues = paddingValues)
            else -> DuplicateList(
                paddingValues = paddingValues,
                duplicateGroups = duplicateGroups
            )
        }
    }
}

@Composable
private fun LoadingState(paddingValues: PaddingValues, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun EmptyState(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "No duplicate files detected.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Your storage looks tidy already.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
private fun DuplicateList(
    paddingValues: PaddingValues,
    duplicateGroups: List<List<com.example.cleanertool.viewmodel.UnnecessaryFile>>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(duplicateGroups, key = { group -> group.first().path }) { group ->
            DuplicateGroupCard(group = group)
        }
    }
}

@Composable
private fun DuplicateGroupCard(group: List<com.example.cleanertool.viewmodel.UnnecessaryFile>) {
    val representative = group.first()
    val duplicates = group.drop(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = representative.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Original + ${duplicates.size} duplicate(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                text = "Will free up ${formatSize(duplicates.sumOf { it.size })}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(top = 4.dp)
            )
            duplicates.forEach { file ->
                Text(
                    text = "• ${file.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF616161),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

