package com.example.cleanertool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.app.Activity
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.math.max
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
// Ads removed from this screen per request
import com.example.cleanertool.viewmodel.ScanViewModel
import com.example.cleanertool.viewmodel.UnnecessaryFile

private fun calcGridHeight(
    count: Int,
    cols: Int = 3,
    maxRows: Int = 3,
    tile: androidx.compose.ui.unit.Dp = 100.dp,
    spacing: androidx.compose.ui.unit.Dp = 6.dp
): androidx.compose.ui.unit.Dp {
    val rows = if (count <= 0) 1 else (count + cols - 1) / cols
    val usedRows = kotlin.math.min(rows, maxRows)
    val rowsF = usedRows.toFloat()
    return tile * rowsF + spacing * (rowsF - 1f).coerceAtLeast(0f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFilesScreen(navController: NavController) {
    val context = LocalContext.current
    val scanViewModel: ScanViewModel = viewModel()
    val unnecessaryFiles by scanViewModel.unnecessaryFiles.collectAsState()
    val isScanning by scanViewModel.isScanning.collectAsState()
    val activity = context as? Activity

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            android.widget.Toast.makeText(context, "Selected files removed", android.widget.Toast.LENGTH_SHORT).show()
            scanViewModel.scanDevice(context)
        } else {
            android.widget.Toast.makeText(context, "Delete cancelled or failed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var selectedFiles by remember { mutableStateOf(setOf<String>()) }

    // Handle URIs that require user confirmation or legacy deletion
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!isScanning && unnecessaryFiles.isEmpty()) {
            scanViewModel.scanDevice(context)
        }
    }

    var pendingLegacyUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var showLegacyPermissionDialog by remember { mutableStateOf(false) }
    var showManualDeleteDialog by remember { mutableStateOf(false) }

    // Show all scanned unnecessary files (sorted by size desc)
    val largeFiles = remember(unnecessaryFiles) {
        unnecessaryFiles.sortedByDescending { it.size }
    }

    val requestWritePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            coroutineScope.launch {
                val remaining2 = scanViewModel.attemptLegacyDeletion(context, pendingLegacyUris)
                if (remaining2.isEmpty()) {
                    android.widget.Toast.makeText(context, "Selected files removed", android.widget.Toast.LENGTH_SHORT).show()
                    scanViewModel.scanDevice(context)
                } else {
                    android.widget.Toast.makeText(context, "Unable to delete some files after granting permission.", android.widget.Toast.LENGTH_LONG).show()
                    pendingLegacyUris = remaining2
                    showManualDeleteDialog = true
                }
                pendingLegacyUris = emptyList()
                showLegacyPermissionDialog = false
            }
        } else {
            android.widget.Toast.makeText(context, "Permission denied. Unable to delete some files.", android.widget.Toast.LENGTH_LONG).show()
            showLegacyPermissionDialog = false
        }
    }

    // SAF folder picker (user selects folder, we try SAF-based deletion)
    val openTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                coroutineScope.launch {
                    val remaining = scanViewModel.attemptSafDeletionWithTree(context, uri, pendingLegacyUris)
                    if (remaining.isEmpty()) {
                        android.widget.Toast.makeText(context, "Files removed", android.widget.Toast.LENGTH_SHORT).show()
                        scanViewModel.scanDevice(context)
                        pendingLegacyUris = emptyList()
                        showManualDeleteDialog = false
                    } else {
                        pendingLegacyUris = remaining
                        showManualDeleteDialog = true
                        android.widget.Toast.makeText(context, "Some files still couldn't be removed.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (t: Throwable) {
                android.widget.Toast.makeText(context, "Failed to obtain folder access", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // If there are pending legacy URIs or the manual dialog flag is set, show a manual-delete dialog
    if (showManualDeleteDialog || pendingLegacyUris.isNotEmpty()) {
        val unresolved = pendingLegacyUris
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showManualDeleteDialog = false },
            title = { Text("Manual deletion required") },
            text = {
                Column {
                    Text("Some files couldn't be deleted automatically. You can open each file to remove it manually or grant folder access to let the app try to remove them.")
                    Spacer(modifier = Modifier.height(8.dp))
                    unresolved.take(20).forEach { uri ->
                        val f = largeFiles.find { it.uri == uri }
                        val label = f?.name ?: uri.toString()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, modifier = Modifier.weight(1f))
                            androidx.compose.material3.TextButton(onClick = {
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
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showManualDeleteDialog = false }) { Text("Close") } },
            dismissButton = {}
        )
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
                val remaining = scanViewModel.attemptLegacyDeletion(context, uris)
                if (remaining.isEmpty()) {
                    android.widget.Toast.makeText(context, "Selected files removed", android.widget.Toast.LENGTH_SHORT).show()
                    scanViewModel.scanDevice(context)
                } else {
                    pendingLegacyUris = remaining
                    val needsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        showLegacyPermissionDialog = true
                    } else {
                        showManualDeleteDialog = true
                    }
                }
            }
        }
    }
    LaunchedEffect(largeFiles) {
        selectedFiles = largeFiles.map { it.path }.toSet()
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                    onClick = { showDeleteConfirm = true },
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
            // Showing all files (no threshold)
            when {
                isScanning -> LoadingIndicator(message = "Scanning files…")
                largeFiles.isEmpty() -> EmptyLargeFilesState()
                else -> Column(modifier = Modifier.fillMaxSize()) {
                            // Split into sections: Images, Videos, Documents, Others
                            val images = largeFiles.filter { it.type == com.example.cleanertool.viewmodel.FileType.IMAGE }
                            val videos = largeFiles.filter { it.type == com.example.cleanertool.viewmodel.FileType.VIDEO }
                            val docs = largeFiles.filter { it.type == com.example.cleanertool.viewmodel.FileType.DOCUMENT }
                            val others = largeFiles.filter { it.type != com.example.cleanertool.viewmodel.FileType.IMAGE && it.type != com.example.cleanertool.viewmodel.FileType.VIDEO && it.type != com.example.cleanertool.viewmodel.FileType.DOCUMENT }

                            // Helper to toggle a set of paths (select all / clear)
                            fun toggleSection(selectAll: Boolean, items: List<com.example.cleanertool.viewmodel.UnnecessaryFile>) {
                                selectedFiles = if (selectAll) selectedFiles + items.map { it.path } else selectedFiles - items.map { it.path }
                            }

                            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                                if (images.isNotEmpty()) {
                                    item { FileSection(title = "Photos", count = images.size, allSelected = images.all { selectedFiles.contains(it.path) }, onSelectAll = { toggleSection(it, images) }) }
                                    item {
                                        val gridHeight = remember(images.size) { calcGridHeight(images.size, cols = 3, maxRows = 3) }

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp)
                                                .height(gridHeight)
                                        ) {
                                            gridItems(images, key = { it.path }) { file ->
                                                FileGridItem(file = file, checked = selectedFiles.contains(file.path), onToggle = { path -> selectedFiles = if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path })
                                            }
                                        }
                                    }
                                }

                                if (videos.isNotEmpty()) {
                                    item { FileSection(title = "Videos", count = videos.size, allSelected = videos.all { selectedFiles.contains(it.path) }, onSelectAll = { toggleSection(it, videos) }) }
                                    item {
                                        val gridHeight = remember(videos.size) { calcGridHeight(videos.size, cols = 3, maxRows = 3) }

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp)
                                                .height(gridHeight)
                                        ) {
                                            gridItems(videos, key = { it.path }) { file ->
                                                FileGridItem(file = file, checked = selectedFiles.contains(file.path), onToggle = { path -> selectedFiles = if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path })
                                            }
                                        }
                                    }
                                }

                                if (docs.isNotEmpty()) {
                                    item { FileSection(title = "Documents", count = docs.size, allSelected = docs.all { selectedFiles.contains(it.path) }, onSelectAll = { toggleSection(it, docs) }) }
                                    item {
                                        val gridHeight = remember(docs.size) { calcGridHeight(docs.size, cols = 3, maxRows = 3) }

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp)
                                                .height(gridHeight)
                                        ) {
                                            gridItems(docs, key = { it.path }) { file ->
                                                FileGridItem(file = file, checked = selectedFiles.contains(file.path), onToggle = { path -> selectedFiles = if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path })
                                            }
                                        }
                                    }
                                }

                                if (others.isNotEmpty()) {
                                    item { FileSection(title = "Other Files", count = others.size, allSelected = others.all { selectedFiles.contains(it.path) }, onSelectAll = { toggleSection(it, others) }) }
                                    item {
                                        val gridHeight = remember(others.size) { calcGridHeight(others.size, cols = 3, maxRows = 3) }

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp)
                                                .height(gridHeight)
                                        ) {
                                            gridItems(others, key = { it.path }) { file ->
                                                FileGridItem(file = file, checked = selectedFiles.contains(file.path), onToggle = { path -> selectedFiles = if (selectedFiles.contains(path)) selectedFiles - path else selectedFiles + path })
                                            }
                                        }
                                    }
                                }
                            }
                    
                    // Ads removed from this screen
                }
            }
        }
    }

    // Confirmation dialog for deletion
    if (showDeleteConfirm) {
        val count = selectedFiles.size
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(text = "Delete $count file(s)?") },
            text = { Text("This will permanently delete the selected files from your device.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteConfirm = false
                    val filesToDelete = largeFiles.filter { selectedFiles.contains(it.path) }
                    if (filesToDelete.isNotEmpty()) {
                        scanViewModel.deleteSpecificFiles(context, filesToDelete) { progress ->
                            if (progress >= 100) {
                                Toast.makeText(context, "Selected files removed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        // Clear selection of deleted paths
                        selectedFiles = selectedFiles - filesToDelete.map { it.path }
                    }
                }) {
                    Text("Delete", color = Color(0xFFFF5722))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FileSection(
    title: String,
    count: Int,
    allSelected: Boolean,
    onSelectAll: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$title ($count)", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Select all", color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
            Checkbox(checked = allSelected, onCheckedChange = { onSelectAll(it) }, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Color(0xFFFF9800)))
        }
    }
}

@Composable
private fun FileGridItem(
    file: com.example.cleanertool.viewmodel.UnnecessaryFile,
    checked: Boolean,
    onToggle: (String) -> Unit
) {
    val thumbSizeModifier = Modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(6.dp))

    Card(
        modifier = Modifier
            .padding(2.dp)
            .then(thumbSizeModifier)
            .clickable { onToggle(file.path) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (file.type == com.example.cleanertool.viewmodel.FileType.IMAGE || file.type == com.example.cleanertool.viewmodel.FileType.VIDEO) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(file.uri ?: file.path)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (file.type == com.example.cleanertool.viewmodel.FileType.VIDEO) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                }
            } else {
                // Document or other - show generic icon
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Description, contentDescription = file.name, modifier = Modifier.size(36.dp), tint = Color.Gray)
                }
            }

            // Selection overlay
            if (checked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                ) {}
            }

            // Check mark
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (checked) Color(0xFFFF9800) else Color.White.copy(alpha = 0.8f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (checked) Icon(Icons.Default.Check, contentDescription = "selected", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: com.example.cleanertool.viewmodel.UnnecessaryFile,
    checked: Boolean,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // small spacing

            // Thumbnail (images/videos) or icon (documents/other)
            val thumbSize = 64.dp
            if (file.type == com.example.cleanertool.viewmodel.FileType.IMAGE || file.type == com.example.cleanertool.viewmodel.FileType.VIDEO) {
                val painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(file.uri ?: file.path)
                        .crossfade(true)
                        .build()
                )
                Image(
                    painter = painter,
                    contentDescription = file.name,
                    modifier = Modifier
                        .size(thumbSize)
                        .padding(end = 12.dp),
                    contentScale = ContentScale.Crop
                )
                if (file.type == com.example.cleanertool.viewmodel.FileType.VIDEO) {
                    // Play overlay
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier
                        .size(20.dp)
                        .alpha(0.9f))
                }
            } else {
                // Generic icon for documents/others
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.padding(end = 12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Medium)
                Text(formatSize(file.size), color = Color(0xFFFF9800))
                Text(file.path, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Checkbox(checked = checked, onCheckedChange = { onToggle(file.path) }, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Color(0xFFFF9800)))
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
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
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

