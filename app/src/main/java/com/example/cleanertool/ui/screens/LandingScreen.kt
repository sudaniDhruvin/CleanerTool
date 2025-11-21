package com.example.cleanertool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun LandingScreen(navController: NavController) {
    var progress by remember { mutableStateOf(0) }
    
    // Auto-start progress when screen loads
    LaunchedEffect(Unit) {
        // Simulate progress from 0 to 100%
        for (i in 0..100) {
            progress = i
            delay(30) // 30ms per percent = ~3 seconds total
        }
        // Navigate to home after progress completes
        navController.navigate("home") {
            popUpTo("landing") { inclusive = true }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // 3D Illustration Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                // Blurred decorative shapes
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .offset(x = (-80).dp, y = 0.dp)
                        .background(
                            Color(0xFFFF9800).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(50)
                        )
                        .blur(20.dp)
                        .zIndex(0f)
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .offset(x = 100.dp, y = (-40).dp)
                        .background(
                            Color(0xFFFF4081).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(50)
                        )
                        .blur(20.dp)
                        .zIndex(0f)
                )
                
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .offset(x = 120.dp, y = 40.dp)
                        .background(
                            Color(0xFFE91E63).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(50)
                        )
                        .blur(20.dp)
                        .zIndex(0f)
                )
                
                // Trash Can (Light Blue Cylinder)
                Box(
                    modifier = Modifier
                        .size(120.dp, 160.dp)
                        .background(
                            Color(0xFF81D4FA),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .zIndex(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Recycling Symbol (Dark Blue) - using Delete icon as recycling symbol
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = Color(0xFF0277BD)
                    )
                    
                    // Purple Image Icon peeking out
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(x = (-20).dp, y = (-30).dp)
                            .background(
                                Color(0xFF9C27B0),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .zIndex(2f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                    
                    // Orange Microphone Icon peeking out
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(x = 20.dp, y = (-30).dp)
                            .background(
                                Color(0xFFFF9800),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .zIndex(2f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                }
                
                // Yellow TXT Document Icon floating above
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .offset(x = (-40).dp, y = (-80).dp)
                        .background(
                            Color(0xFFFFEB3B),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .zIndex(3f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TXT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App Title
            Text(
                text = "Cleaner Toolbox",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Progress Bar Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(8.dp),
                    color = Color(0xFF2196F3),
                    trackColor = Color(0xFFBBDEFB)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Please wait a moment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF757575)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

