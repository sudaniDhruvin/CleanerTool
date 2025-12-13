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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.cleanertool.utils.PermissionUtils

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoPermissionScreen(navController: NavController) {
    val permissions = PermissionUtils.getStoragePermissions().toList()
    val permissionsState = rememberMultiplePermissionsState(permissions)
    
    // Navigate to scanning screen when permissions granted
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            navController.navigate("photo_scanning") {
                popUpTo("photo_permission") { inclusive = true }
            }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Illustration section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Phone outline
                    Box(
                        modifier = Modifier
                            .size(120.dp, 200.dp)
                            .background(
                                Color(0xFFE3F2FD),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(8.dp)
                    ) {
                        // Phone screen content
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(Color(0xFF2196F3), RoundedCornerShape(4.dp))
                            )
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(Color(0xFFBBDEFB), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        
                        // Camera dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .offset(x = 56.dp, y = 0.dp)
                                .background(Color(0xFF2196F3), RoundedCornerShape(4.dp))
                        )
                    }
                    
                    // Yellow folders
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .offset(x = (-80).dp, y = 20.dp)
                            .background(Color(0xFFFFEB3B), RoundedCornerShape(8.dp))
                    )
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .offset(x = (-60).dp, y = 40.dp)
                            .background(Color(0xFFFFC107), RoundedCornerShape(8.dp))
                    )
                    
                    // Toggle switch
                    Box(
                        modifier = Modifier
                            .size(40.dp, 20.dp)
                            .offset(x = 40.dp, y = (-20).dp)
                            .background(Color(0xFF81D4FA), RoundedCornerShape(10.dp))
                    )
                    
                    // Checkmark
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .offset(x = 60.dp, y = (-40).dp)
                            .background(Color(0xFF2196F3), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Shield icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .offset(x = 60.dp, y = 60.dp)
                            .background(Color(0xFF81D4FA), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Text content
                Text(
                    text = "We need media authorization to provide you\nwith cleaning services. You don't need to\nworry about any privacy disclosure. We won't\ndisclose any of your data.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF424242),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Grant button
                Button(
                    onClick = {
                        permissionsState.launchMultiplePermissionRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text(
                        text = "Grant",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Banner Ad
                BannerAdView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

