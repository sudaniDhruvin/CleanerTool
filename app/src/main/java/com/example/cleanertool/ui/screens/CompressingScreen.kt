package com.example.cleanertool.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.viewmodel.StorageViewModel
import kotlinx.coroutines.delay

@Composable
fun CompressingScreen(
    navController: NavController,
    onComplete: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val viewModelStoreOwner = checkNotNull(LocalContext.current as? androidx.lifecycle.ViewModelStoreOwner)
    val viewModel: StorageViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = viewModelStoreOwner
    )
    val selectedImageData by viewModel.selectedImageData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var compressionStarted by remember { mutableStateOf(false) }
    var compressionResult by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Perform compression when screen loads
    LaunchedEffect(Unit) {
        if (selectedImageData != null && !compressionStarted) {
            compressionStarted = true
            viewModel.compressSingleImage(
                context, 
                selectedImageData!!,
                onComplete = { size ->
                    compressionResult = size != null
                },
                onError = { errorMsg ->
                    errorMessage = errorMsg
                    compressionResult = false
                }
            )
        } else if (selectedImageData == null) {
            delay(2000)
            errorMessage = "No image selected"
            onComplete(false)
        }
    }
    
    // Monitor compression completion
    LaunchedEffect(isLoading, compressionResult, errorMessage) {
        if (compressionStarted && !isLoading) {
            if (compressionResult == true) {
                // Success - wait minimum 2 seconds for animation
                delay(2000)
                onComplete(true)
            } else if (errorMessage != null || error != null) {
                // Error occurred - navigate to error screen
                delay(1500) // Show compressing animation briefly
                navController.navigate("compression_error?error=${android.net.Uri.encode(errorMessage ?: error ?: "Unknown error")}") {
                    popUpTo("compressing") { inclusive = true }
                }
            }
        }
    }
    // Animated dots
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    // Rotating animation for the circular lines
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2196F3))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Landscape image icon with rotating circles
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Rotating semi-circles
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .rotate(rotation)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawArc(
                            color = Color(0xFF81D4FA).copy(alpha = 0.5f),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .rotate(rotation + 180f)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawArc(
                            color = Color(0xFF81D4FA).copy(alpha = 0.5f),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }
                
                // Central image frame
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                ) {
                    // Cloud shape at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFBBDEFB))
                    )
                    
                    // Circle (sun) at top right
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .offset(x = 30.dp, y = 20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFBBDEFB))
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Compressing text
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Compressing",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Animated dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = dot1Alpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = dot2Alpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = dot3Alpha))
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Wait a moment please",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

