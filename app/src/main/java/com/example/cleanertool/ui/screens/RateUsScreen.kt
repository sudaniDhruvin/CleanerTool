package com.example.cleanertool.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.ads.NativeAdView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateUsScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedRating by remember { mutableStateOf(0) }
    var showThankYouDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Rate Us",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2196F3),
                                Color(0xFF1976D2)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "App Icon",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Enjoying Cleaner Toolbox?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Your feedback helps us improve!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Star rating
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 1..5) {
                    IconButton(
                        onClick = { selectedRating = i }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "$i stars",
                            tint = if (i <= selectedRating) Color(0xFFFFD700) else Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Rate button
            Button(
                onClick = {
                    if (selectedRating >= 4) {
                        // Open Play Store
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("market://details?id=${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to web browser
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                        showThankYouDialog = true
                    } else {
                        // Show feedback option
                        showThankYouDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedRating > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Text(
                    text = if (selectedRating >= 4) "Rate on Play Store" else "Submit Rating",
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Native Ad
            Spacer(modifier = Modifier.height(32.dp))
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
                    .padding(horizontal = 16.dp)
            )
        }
    }
    
    // Thank you dialog
    if (showThankYouDialog) {
        AlertDialog(
            onDismissRequest = { 
                showThankYouDialog = false
                navController.popBackStack()
            },
            title = { Text("Thank You!") },
            text = { 
                Text(
                    if (selectedRating >= 4) 
                        "Thank you for your 5-star rating!" 
                    else 
                        "Thank you for your feedback! We'll work on improving."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showThankYouDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

