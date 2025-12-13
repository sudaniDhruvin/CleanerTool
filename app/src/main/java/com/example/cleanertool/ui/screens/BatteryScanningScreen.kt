package com.example.cleanertool.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.ads.NativeAdView
import kotlinx.coroutines.delay

@Composable
fun BatteryScanningScreen(navController: NavController) {
    // Animation for scanning line
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_offset"
    )
    
    // Auto-navigate after animation completes
    LaunchedEffect(Unit) {
        delay(3000) // Show scanning for 3 seconds
        navController.navigate("battery_charging") {
            popUpTo("battery_scanning") { inclusive = true }
        }
    }
    
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
            Spacer(modifier = Modifier.weight(1f))
            
            // Battery graphic with scanning animation
            Box(
                modifier = Modifier
                    .size(280.dp, 200.dp),
                contentAlignment = Alignment.Center
            ) {
                // Corner brackets (L-shaped frames)
                Box(
                    modifier = Modifier
                        .size(280.dp, 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    // Top-left bracket
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = 0.dp)
                            .size(40.dp, 4.dp)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = 0.dp)
                            .size(4.dp, 40.dp)
                            .background(Color.White)
                    )
                    
                    // Top-right bracket
                    Box(
                        modifier = Modifier
                            .offset(x = 236.dp, y = 0.dp)
                            .size(40.dp, 4.dp)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = 276.dp, y = 0.dp)
                            .size(4.dp, 40.dp)
                            .background(Color.White)
                    )
                    
                    // Bottom-left bracket
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = 196.dp)
                            .size(40.dp, 4.dp)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = 156.dp)
                            .size(4.dp, 40.dp)
                            .background(Color.White)
                    )
                    
                    // Bottom-right bracket
                    Box(
                        modifier = Modifier
                            .offset(x = 236.dp, y = 196.dp)
                            .size(40.dp, 4.dp)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = 276.dp, y = 156.dp)
                            .size(4.dp, 40.dp)
                            .background(Color.White)
                    )
                }
                
                // Battery shape (translucent light blue with grid pattern)
                Box(
                    modifier = Modifier
                        .size(200.dp, 120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF81D4FA).copy(alpha = 0.6f),
                                    Color(0xFFBBDEFB).copy(alpha = 0.6f)
                                )
                            )
                        )
                ) {
                    // Grid pattern overlay
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(8) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )
                        }
                    }
                    
                    // Lightning bolt icon
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .align(Alignment.Center)
                            .offset(x = (-20).dp, y = (-10).dp),
                        tint = Color(0xFFFFEB3B)
                    )
                    
                    // Scanning line animation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .offset(x = (scanOffset * 200).dp - 200.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF64B5F6).copy(alpha = 0.8f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                
                // Light blue bar below battery
                Box(
                    modifier = Modifier
                        .offset(y = 80.dp)
                        .width(180.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF81D4FA))
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Analyzing text
            Text(
                text = "Analyzing battery status",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Animated dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                repeat(3) { index ->
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 600,
                                delayMillis = index * 200,
                                easing = LinearEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_alpha_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = dotAlpha))
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Native Ad
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
                    .padding(horizontal = 0.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

