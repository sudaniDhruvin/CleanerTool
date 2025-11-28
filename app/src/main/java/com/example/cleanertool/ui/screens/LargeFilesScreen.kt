package com.example.cleanertool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.viewmodel.ScanViewModel
import com.example.cleanertool.viewmodel.UnnecessaryFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFilesScreen(navController: NavController) {
    val context = LocalContext.current
    val scanViewModel: ScanViewModel = viewModel()
    val unnecessaryFiles by scanViewModel.unnecessaryFiles.collectAsState()
    val isScanning by scanViewModel.isScanning.collectAsState()

    var thresholdMb by remember { mutableStateOf(50f) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        if (!isScanning && unnecessaryFiles.isEmpty()) {
            scanViewModel.scanDevice(context)
        }
    }

    val largeFiles = remember(unnecessaryFiles, thresholdMb) {
        scanViewModel.getLargeFiles((thresholdMb * 1024 * 1024).toLong())
    }

    LaunchedEffect(largeFiles) {
        selectedFiles = largeFiles.map { it.path }.toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Large Files", fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFF9800))
            )
        },
        bottomBar = {
            if (selectedFiles.isNotEmpty()) {
                Button(
                    onClick = {
                        val filesToDelete = largeFiles.filter { selectedFiles.contains(it.path) }
                        if (filesToDelete.isEmpty()) return@Button
                        scanViewModel.deleteSpecificFiles(context, filesToDelete) { progress ->
                            if (progress >= 100) {
                                Toast.makeText(context, "Selected files removed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    enabled = selectedFiles.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Delete selected (${selectedFiles.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ThresholdSlider(thresholdMb = thresholdMb) { thresholdMb = it }
            when {
                isScanning -> LoadingIndicator(message = "Scanning files…")
                largeFiles.isEmpty() -> EmptyLargeFilesState()
                else -> FileList(
                    files = largeFiles,
                    selected = selectedFiles,
                    onToggle = { path ->
                        selectedFiles =
                            if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path
                    }
                )
            }
        }
    }
}

@Composable
private fun ThresholdSlider(
    thresholdMb: Float,
    onThresholdChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show files larger than", fontWeight = FontWeight.Medium)
            Text("${thresholdMb.toInt()} MB", fontWeight = FontWeight.Bold)
        }
        Slider(
            value = thresholdMb,
            onValueChange = onThresholdChanged,
            valueRange = 10f..1024f,
            steps = 100,
            colors = SliderDefaults.colors(
                activeTrackColor = Color(0xFFFF9800),
                thumbColor = Color(0xFFFF9800)
            )
        )
    }
}

@Composable
private fun FileList(
    files: List<UnnecessaryFile>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(files, key = { it.path }) { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, fontWeight = FontWeight.Bold)
                        Text(formatSize(file.size), color = Color(0xFFFF9800))
                        Text(file.path, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Checkbox(
                        checked = selected.contains(file.path),
                        onCheckedChange = { onToggle(file.path) },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF9800))
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLargeFilesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No large files detected at this threshold.", fontWeight = FontWeight.Bold)
        Text(
            "Try lowering the slider to reveal smaller items.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
private fun LoadingIndicator(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Text(message, modifier = Modifier.padding(top = 12.dp))
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

