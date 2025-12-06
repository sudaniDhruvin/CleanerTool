package com.example.cleanertool.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.cleanertool.navigation.Screen
import com.example.cleanertool.viewmodel.StorageViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.cleanertool.utils.PermissionUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StorageGalleryScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModelStoreOwner = checkNotNull(LocalContext.current as? androidx.lifecycle.ViewModelStoreOwner) {
        "Context must be a ViewModelStoreOwner"
    }
    val viewModel: StorageViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = viewModelStoreOwner
    )
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val permissions = PermissionUtils.getStoragePermissions().toList()
    val permissionsState = rememberMultiplePermissionsState(permissions)

    // Get tab parameter from navigation (if coming from compression success)
    var selectedTab by remember { mutableStateOf(0) } // 0 = All photos, 1 = Compressed
    var showCompressionDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf("Size") } // "Time" or "Size"
    
    // Auto-scan images when screen loads
    LaunchedEffect(Unit) {
        if (permissionsState.allPermissionsGranted && images.isEmpty()) {
            viewModel.scanImages(context)
        }
    }
    
    // Switch to compressed tab when we have compressed images
    LaunchedEffect(images) {
        val hasCompressedImages = images.any { it.isCompressed }
        if (hasCompressedImages && selectedTab == 0) {
            delay(300)
            selectedTab = 1
        }
    }

    // Handle hardware back button - navigate to Home instead of scanning screen
    BackHandler {
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Photo Compression",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Sort", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    ErrorContent(
                        error = error!!,
                        onRetry = { viewModel.scanImages(context) }
                    )
                }
                images.isEmpty() -> {
                    EmptyStateContent(onScanClick = { viewModel.scanImages(context) })
                }
                else -> {
                    // Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TabButton(
                            text = "All photos",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        TabButton(
                            text = "Compressed",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    val filteredImages = if (selectedTab == 0) {
                        // All photos - show only uncompressed images (original images that haven't been compressed)
                        images.filter { !it.isCompressed }
                    } else {
                        // Compressed tab - show only compressed images
                        images.filter { it.isCompressed }
                    }
                    
                    // Sort images based on selected sort order
                    val sortedImages = when (sortOrder) {
                        "Time" -> filteredImages.sortedByDescending { it.dateModified }
                        "Size" -> filteredImages.sortedByDescending { 
                            // Use appropriate size based on tab
                            if (selectedTab == 0 && it.isCompressed) it.originalSize else it.size
                        }
                        else -> filteredImages
                    }
                    
                    if (selectedTab == 1 && sortedImages.isEmpty()) {
                        // Empty state for compressed tab
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.Gray
                                )
                                Text(
                                    text = "No Compressed Images",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Compress images from the 'All photos' tab to see them here",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        ImageGrid(
                            images = sortedImages,
                            viewModel = viewModel,
                            navController = navController,
                            selectedTab = selectedTab,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showCompressionDialog) {
        CompressionDialog(
            onDismiss = { showCompressionDialog = false },
            images = images,
            viewModel = viewModel
        )
    }
    
    // Sort menu popup
    if (showSortMenu) {
        SortMenuPopup(
            currentSort = sortOrder,
            onDismiss = { showSortMenu = false },
            onSortSelected = { sort ->
                sortOrder = sort
                showSortMenu = false
            }
        )
    }
    
    // Sort menu popup
    if (showSortMenu) {
        SortMenuPopup(
            currentSort = sortOrder,
            onDismiss = { showSortMenu = false },
            onSortSelected = { sort ->
                sortOrder = sort
                showSortMenu = false
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestContent(
    permissionsState: com.google.accompanist.permissions.MultiplePermissionsState,
    onPermissionsGranted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Storage Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "We need access to your photos to scan and manage them",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                permissionsState.launchMultiplePermissionRequest()
            }
        ) {
            Text("Grant Permission")
        }
        
        LaunchedEffect(permissionsState.allPermissionsGranted) {
            if (permissionsState.allPermissionsGranted) {
                onPermissionsGranted()
            }
        }
    }
}

@Composable
fun EmptyStateContent(onScanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Images Found",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the button below to scan your device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onScanClick) {
            Icon(Icons.Default.Search, "Scan")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Images")
        }
    }
}

@Composable
fun ErrorContent(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun ImageGrid(
    images: List<com.example.cleanertool.data.ImageData>,
    viewModel: StorageViewModel,
    navController: NavController,
    selectedTab: Int,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(4.dp)
    ) {
        items(images) { image ->
            ImageItem(
                image = image,
                selectedTab = selectedTab,
                onClick = {
                    // Store selected image data in ViewModel first
                    viewModel.setSelectedImage(image)
                    // Navigate immediately - ViewModel state update is synchronous
                    navController.navigate("image_detail")
                }
            )
        }
    }
}

@Composable
fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF2196F3) else Color.White,
            contentColor = if (selected) Color.White else Color(0xFF2196F3)
        ),
        border = if (!selected) {
            ButtonDefaults.outlinedButtonBorder.copy(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color(0xFF2196F3), Color(0xFF2196F3))
                )
            )
        } else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ImageItem(
    image: com.example.cleanertool.data.ImageData,
    selectedTab: Int = 0,
    onClick: () -> Unit = {}
) {
    // Determine which size to display:
    // - In "All photos" tab (0): show originalSize for compressed images, size for non-compressed
    // - In "Compressed" tab (1): show size (compressed size)
    val displaySize = if (selectedTab == 0 && image.isCompressed) {
        // All photos tab - show original size for compressed images
        image.originalSize
    } else {
        // Compressed tab or non-compressed images - show current size
        image.size
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(image.uri)
                        .crossfade(true)
                        .build()
                ),
                contentDescription = image.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // File size overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = formatFileSize(displaySize),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            
            // Compressed badge
            if (image.isCompressed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "✓",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

@Composable
fun SortMenuPopup(
    currentSort: String,
    onDismiss: () -> Unit,
    onSortSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(200.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF424242)
            ),
            onClick = { /* Prevent dismiss on card click */ }
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // Time option
                Text(
                    text = "Time",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortSelected("Time") }
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (currentSort == "Time") Color(0xFF2196F3) else Color.White
                )
                
                // Size option
                Text(
                    text = "Size",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortSelected("Size") }
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (currentSort == "Size") Color(0xFF2196F3) else Color.White
                )
            }
        }
    }
}

@Composable
fun CompressionDialog(
    onDismiss: () -> Unit,
    images: List<com.example.cleanertool.data.ImageData>,
    viewModel: StorageViewModel
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val compressionProgress by viewModel.compressionProgress.collectAsState()
    val error by viewModel.error.collectAsState()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Compress Images") },
        text = {
            Column {
                if (isLoading) {
                    if (compressionProgress != null) {
                        Text("Compressing images... ${compressionProgress!!.first}/${compressionProgress!!.second}")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = compressionProgress!!.first.toFloat() / compressionProgress!!.second.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Compressing images...")
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                } else {
                    Text("This will compress all images by 60% (reducing file size while maintaining quality).")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total images: ${images.size}")
                }
                if (error != null && !isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                TextButton(onClick = {}) {
                    Text("Compressing...")
                }
            } else {
                TextButton(
                    onClick = {
                        viewModel.compressImages(context, images)
                    }
                ) {
                    Text("Compress All")
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
