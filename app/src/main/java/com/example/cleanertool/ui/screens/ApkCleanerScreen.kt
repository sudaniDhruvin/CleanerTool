package com.example.cleanertool.ui.screens

// using fully-qualified android.widget.Toast to avoid import conflicts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.ads.NativeAdView
import com.example.cleanertool.viewmodel.FileType
import com.example.cleanertool.viewmodel.ScanViewModel
import com.example.cleanertool.viewmodel.UnnecessaryFile
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.content.Intent
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.launch

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

    // Handle URIs that require user confirmation to delete
    val activity = context as? Activity
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            android.widget.Toast.makeText(context, "APK files removed", android.widget.Toast.LENGTH_SHORT).show()
            scanViewModel.scanDevice(context)
        } else {
            android.widget.Toast.makeText(context, "Delete cancelled or failed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Legacy deletion state for APK cleaner (shared logic as in LargeFilesScreen)
    val coroutineScope = rememberCoroutineScope()
    var pendingLegacyUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var showLegacyPermissionDialog by remember { mutableStateOf(false) }
    var showManualDeleteDialog by remember { mutableStateOf(false) }
    val requestWritePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            coroutineScope.launch {
                val remaining2 = scanViewModel.attemptLegacyDeletion(context, pendingLegacyUris)
                if (remaining2.isEmpty()) {
                    android.widget.Toast.makeText(context, "APK files removed", android.widget.Toast.LENGTH_SHORT).show()
                    scanViewModel.scanDevice(context)
                } else {
                    android.widget.Toast.makeText(context, "Unable to delete some APK files after granting permission.", android.widget.Toast.LENGTH_LONG).show()
                }
                pendingLegacyUris = emptyList()
                showLegacyPermissionDialog = false
            }
        } else {
            android.widget.Toast.makeText(context, "Permission denied. Unable to delete some APK files.", android.widget.Toast.LENGTH_LONG).show()
            showLegacyPermissionDialog = false
        }
    }

    LaunchedEffect(scanViewModel) {
        scanViewModel.pendingDeleteUris.collectLatest { uris ->
            if (uris.isEmpty()) return@collectLatest
                if (activity == null) {
                android.widget.Toast.makeText(context, "Unable to request delete permission", android.widget.Toast.LENGTH_SHORT).show()
                return@collectLatest
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intentSender = MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
                        val req = IntentSenderRequest.Builder(intentSender).build()
                        deleteLauncher.launch(req)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Failed to start delete confirmation", android.widget.Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Try legacy deletion; if leftovers remain, prompt for permission where possible
                val remaining = scanViewModel.attemptLegacyDeletion(context, uris)
                if (remaining.isEmpty()) {
                    android.widget.Toast.makeText(context, "APK files removed", android.widget.Toast.LENGTH_SHORT).show()
                    scanViewModel.scanDevice(context)
                } else {
                    // Request WRITE_EXTERNAL_STORAGE permission if needed and retry
                    val needsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        // Launch permission request and retry in callback
                        requestWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        pendingLegacyUris = remaining
                        showLegacyPermissionDialog = true
                    } else {
                            // Allow user to manually open/delete the remaining files
                            pendingLegacyUris = remaining
                            showManualDeleteDialog = true
                    }
                }
            }
        }
    }

    // (legacy deletion state defined above)

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
            else -> Column(modifier = Modifier.fillMaxSize()) {
                ApkList(
                    paddingValues = PaddingValues(0.dp),
                    files = apkFiles,
                    selected = selected,
                    onToggle = { path ->
                        selected = if (selected.contains(path)) selected - path else selected + path
                    },
                    modifier = Modifier.weight(1f)
                )

                // Native Ad
                Spacer(modifier = Modifier.height(16.dp))
                NativeAdView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Banner Ad
                Spacer(modifier = Modifier.height(8.dp))
                BannerAdView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Show permission dialog if needed
    if (showLegacyPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showLegacyPermissionDialog = false },
            title = { Text("Storage permission required") },
            text = { Text("To delete these APK files we need storage permission. Grant permission?") },
            confirmButton = {
                TextButton(onClick = {
                    requestWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }) {
                    Text("Grant")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLegacyPermissionDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showManualDeleteDialog || pendingLegacyUris.isNotEmpty()) {
        val unresolved = pendingLegacyUris
        val openTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    coroutineScope.launch {
                        val remaining = scanViewModel.attemptSafDeletionWithTree(context, uri, unresolved)
                        if (remaining.isEmpty()) {
                            android.widget.Toast.makeText(context, "APK files removed", android.widget.Toast.LENGTH_SHORT).show()
                            scanViewModel.scanDevice(context)
                            pendingLegacyUris = emptyList()
                            showManualDeleteDialog = false
                        } else {
                            pendingLegacyUris = remaining
                            android.widget.Toast.makeText(context, "Some APK files couldn't be deleted.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (t: Throwable) {
                    android.widget.Toast.makeText(context, "Failed to obtain folder access", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showManualDeleteDialog = false },
            title = { Text("Manual deletion required") },
            text = {
                Column {
                    Text("Some APK files couldn't be deleted automatically. You can open each file to remove it manually.")
                    Spacer(modifier = Modifier.height(8.dp))
                    unresolved.take(20).forEach { uri ->
                        val f = apkFiles.find { it.uri == uri }
                        val label = f?.name ?: uri.toString()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                try {
                                    val mime = context.contentResolver.getType(uri) ?: "*/*"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mime)
                                        flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Unable to open file", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Open") }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { openTreeLauncher.launch(null) }) { Text("Grant folder access to delete") }
                    if (unresolved.size > 20) Text("...and ${unresolved.size - 20} more")
                }
            },
            confirmButton = { TextButton(onClick = { showManualDeleteDialog = false }) { Text("Close") } },
            dismissButton = {}
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
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(paddingValues),
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


