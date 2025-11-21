package com.example.cleanertool.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.cleanertool.data.ImageData
import com.example.cleanertool.viewmodel.StorageViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailScreen(
    navController: NavController
) {
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
    val selectedImageDataFromViewModel by viewModel.selectedImageData.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    
    // State to track the current image data
    var imageData by remember { mutableStateOf<ImageData?>(null) }
    
    // Update imageData immediately when selectedImageDataFromViewModel is available
    LaunchedEffect(selectedImageDataFromViewModel) {
        if (selectedImageDataFromViewModel != null) {
            imageData = selectedImageDataFromViewModel
        }
    }
    
    // Also try to find from images list if we have URI but no data
    LaunchedEffect(selectedImageUri, images) {
        if (imageData == null && selectedImageUri != null) {
            val found = images.find { 
                it.uri.toString() == selectedImageUri.toString()
            }
            if (found != null) {
                imageData = found
            }
        }
    }
    
    // If still not found, try reloading images
    LaunchedEffect(selectedImageUri, imageData, images, isLoading) {
        if (selectedImageUri != null && imageData == null && images.isEmpty() && !isLoading) {
            viewModel.scanImages(context)
        }
    }
    
    // Wait a bit for images to load, then try finding again
    LaunchedEffect(images, selectedImageUri, imageData) {
        if (imageData == null && selectedImageUri != null && images.isNotEmpty()) {
            delay(100)
            val found = images.find { 
                it.uri.toString() == selectedImageUri.toString()
            }
            if (found != null) {
                imageData = found
            }
        }
    }
    
    val currentImageData = imageData
    val estimatedCompressedSize = currentImageData?.let {
        viewModel.estimateCompressedSize(it.size)
    }
    val isCompressed = currentImageData?.isCompressed == true

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
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            if (currentImageData == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                            Text(
                                text = "Image not found",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Image Display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                ImageRequest.Builder(context)
                                    .data(currentImageData.uri)
                                    .crossfade(true)
                                    .build()
                            ),
                            contentDescription = currentImageData.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        
                        // File size overlay at bottom right
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = viewModel.formatFileSize(currentImageData.size),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                    
                    // Bottom section with compression info and button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        // After compression info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "After compression",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF424242)
                            )
                            Text(
                                text = viewModel.formatFileSize(estimatedCompressedSize ?: currentImageData.size),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Compress button
                        Button(
                            onClick = {
                                if (currentImageData != null) {
                                    navController.navigate("compressing")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isCompressed && !isLoading
                        ) {
                            Text(
                                text = "Compress",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}


