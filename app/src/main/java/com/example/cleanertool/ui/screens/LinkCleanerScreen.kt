package com.example.cleanertool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.ads.NativeAdView
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCleanerScreen(navController: NavController) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var dirtyLink by remember { mutableStateOf("") }
    var cleanedLink by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        dirtyLink = clipboardManager.getText()?.text.orEmpty()
        cleanedLink = sanitizeLink(dirtyLink)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link Cleaner", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF8BC34A))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = dirtyLink,
                onValueChange = {
                    dirtyLink = it
                    cleanedLink = sanitizeLink(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Link with tracking parameters") },
                placeholder = { Text("Paste a link here") },
                minLines = 3
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Clean version", fontWeight = FontWeight.SemiBold)
                Text(
                    text = cleanedLink,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF33691E)
                )
            }

            Button(
                onClick = {
                    dirtyLink = ""
                    cleanedLink = ""
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCDDC39))
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                Text("Reset", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(cleanedLink))
                    Toast.makeText(context, "Clean link copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = cleanedLink.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8BC34A))
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                Text("Copy clean link", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            // Native Ad
            Spacer(modifier = Modifier.height(16.dp))
            NativeAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            // Banner Ad
            Spacer(modifier = Modifier.height(8.dp))
            BannerAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
}

private fun sanitizeLink(rawUrl: String): String {
    if (!rawUrl.contains("?")) return rawUrl
    val parts = rawUrl.split("?", limit = 2)
    val base = parts.first()
    val query = parts.getOrNull(1) ?: return rawUrl
    val cleanedParams = query.split("&")
        .mapNotNull { param ->
            if (param.isBlank()) return@mapNotNull null
            val key = param.substringBefore("=").lowercase()
            val value = param.substringAfter("=", "")
            if (key.startsWith("utm_") ||
                key == "fbclid" ||
                key == "gclid" ||
                key.contains("mc_") ||
                key.contains("yclid")
            ) {
                null
            } else {
                val decodedValue = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrElse { value }
                val safeValue = URLEncoder.encode(decodedValue, StandardCharsets.UTF_8.name())
                "$key=$safeValue"
            }
        }

    return if (cleanedParams.isEmpty()) base else "$base?${cleanedParams.joinToString("&")}"
}

