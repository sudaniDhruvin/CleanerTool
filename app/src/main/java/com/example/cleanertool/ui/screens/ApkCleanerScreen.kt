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
import com.example.cleanertool.viewmodel.FileType
import com.example.cleanertool.viewmodel.ScanViewModel
import com.example.cleanertool.viewmodel.UnnecessaryFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkCleanerScreen(navController: NavController) {
    val context = LocalContext.current
    val scanViewModel: ScanViewModel = viewModel()
    val unnecessaryFiles by scanViewModel.unnecessaryFiles.collectAsState()
    val isScanning by scanViewModel.isScanning.collectAsState()

    var selected by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        if (!isScanning && unnecessaryFiles.isEmpty()) {
            scanViewModel.scanDevice(context)
        }
    }

    val apkFiles = remember(unnecessaryFiles) { scanViewModel.getFilesByCategory(FileType.OBSOLETE_APK) }

    LaunchedEffect(apkFiles) {
        selected = apkFiles.map { it.path }.toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("APK Cleaner", fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF44336))
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Button(
                    onClick = {
                        val files = apkFiles.filter { selected.contains(it.path) }
                        scanViewModel.deleteSpecificFiles(context, files) { progress ->
                            if (progress >= 100) {
                                Toast.makeText(context, "APK files removed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                    Text("Delete selected (${selected.size})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        when {
            isScanning -> LoadingIndicator(message = "Scanning for APK files...")
            apkFiles.isEmpty() -> EmptyApkState(paddingValues)
            else -> ApkList(
                paddingValues = paddingValues,
                files = apkFiles,
                selected = selected,
                onToggle = { path ->
                    selected = if (selected.contains(path)) selected - path else selected + path
                }
            )
        }
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
        Text(message, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun formatApkSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

@Composable
private fun ApkList(
    paddingValues: PaddingValues,
    files: List<UnnecessaryFile>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(files, key = { it.path }) { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name.removeSuffix(".apk"), fontWeight = FontWeight.Bold)
                        Text("Size: ${formatApkSize(file.size)}", color = Color(0xFFF44336))
                        Text(file.path, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Checkbox(
                        checked = selected.contains(file.path),
                        onCheckedChange = { onToggle(file.path) },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF44336))
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyApkState(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No APK leftovers found!", fontWeight = FontWeight.Bold)
        Text(
            "Great job keeping your downloads folder clean.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}


