package com.example.cleanertool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.ads.NativeAdView
import com.example.cleanertool.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Orange background section (upper half)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Color(0xFFFF5722))
                .padding(horizontal = 24.dp)
        ) {
            // Top spacing for status bar
            Spacer(modifier = Modifier.height(48.dp))
            
            // Header with app name on left and icons on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Title on left
                Text(
                    text = "Cleaner Toolbox",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // Lock and Settings icons on right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock icon
                    IconButton(
                        onClick = { navController.navigate("permission_management") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Settings icon
                    IconButton(
                        onClick = { navController.navigate("settings_menu") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Large CLEAN circle button (now clickable + pulsing animation)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing animation + expanding background waves
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                // Five staggered expanding waves behind the button (darker tone)
                val waveCount = 5
                val waveDuration = 1600
                val waveMaxScale = 1.8f
                val waveDelayStep = waveDuration / waveCount
                val waveStates = List(waveCount) { idx ->
                    infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = waveDuration, easing = LinearOutSlowInEasing, delayMillis = idx * waveDelayStep),
                            repeatMode = RepeatMode.Restart
                        )
                    )
                }

                // Expanding wave layers (behind the outer ring)
                // Render waves from largest (furthest back) to smallest so they appear layered
                val waveColor = Color(0xFFB71C1C) // dark red
                for (i in waveCount - 1 downTo 0) {
                    val w = waveStates[i].value
                    // Slightly reduce alpha for later waves for depth
                    val alpha = (0.22f - i * 0.03f).coerceAtLeast(0.05f)
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .scale(1f + w * waveMaxScale)
                            .background(waveColor.copy(alpha = alpha), shape = CircleShape)
                    )
                }

                // Outer ring with red gradient border
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(scale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF7043),
                                    Color(0xFFFF5722)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Inner white circle (clickable to trigger Junk Cleaner / Scan)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(scale)
                        .background(Color.White, shape = CircleShape)
                        .clickable(onClick = { navController.navigate(Screen.Scan.route) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CLEAN",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
        
        // White background section (lower half)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(Color.White)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top spacing to account for Junk Cleaner button
            Spacer(modifier = Modifier.height(40.dp))
            
            // More Tools section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Text(
                    text = "More Tools",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Battery Info (Green)
                    ToolCard(
                        title = "Battery Info",
                        icon = Icons.Default.Settings,
                        color = Color(0xFF4CAF50),
                        onClick = { navController.navigate(Screen.BatteryScanning.route) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // App Process (Blue)
                    ToolCard(
                        title = "App Process",
                        icon = Icons.Default.Phone,
                        color = Color(0xFF2196F3),
                        onClick = { navController.navigate(Screen.AppProcessScanning.route) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Photo Compression (Purple)
                    ToolCard(
                        title = "Photo Compression",
                        icon = Icons.Default.Star,
                        color = Color(0xFF9C27B0),
                        onClick = { navController.navigate("photo_permission") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // White-background utility cards below More Tools
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "More Utilities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF212121),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "Notification Cleaner",
                        icon = Icons.Default.Notifications,
                        color = Color.White,
                        contentColor = Color(0xFF212121),
                        onClick = { navController.navigate("notification_cleaner") },
                        modifier = Modifier.weight(1f)
                    )

                    ToolCard(
                        title = "Large Files",
                        icon = Icons.Default.Folder,
                        color = Color.White,
                        contentColor = Color(0xFF212121),
                        onClick = { navController.navigate(Screen.LargeFiles.route) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolCard(
                        title = "Uninstall Apps",
                        icon = Icons.Default.Delete,
                        color = Color.White,
                        contentColor = Color(0xFF212121),
                        onClick = { navController.navigate(Screen.UninstallReminder.route) },
                        modifier = Modifier.weight(1f)
                    )

                    ToolCard(
                        title = "Speaker Cleaner",
                        icon = Icons.Default.Speaker,
                        color = Color.White,
                        contentColor = Color(0xFF212121),
                        onClick = { navController.navigate(Screen.SpeakerMaintenance.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Native Ad
            Spacer(modifier = Modifier.height(16.dp))
            NativeAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            
            // Banner Ad at the bottom
            Spacer(modifier = Modifier.height(16.dp))
            BannerAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp)
            )
            
            
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Junk Cleaner card removed — functionality moved to the CLEAN circle above
    }
}

@Composable
fun ToolCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(40.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

