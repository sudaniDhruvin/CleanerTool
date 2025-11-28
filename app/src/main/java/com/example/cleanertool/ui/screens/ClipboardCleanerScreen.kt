package com.example.cleanertool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardCleanerScreen(navController: NavController) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var clipboardText by remember { mutableStateOf("") }
    var cleanedText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        clipboardText = clipboardManager.getText()?.text.orEmpty()
        cleanedText = removeTrackingParams(clipboardText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clipboard Cleaner", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF03A9F4))
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current clipboard", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = clipboardText,
                        onValueChange = {
                            clipboardText = it
                            cleanedText = removeTrackingParams(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        placeholder = { Text("Nothing copied yet") },
                        minLines = 3
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Clean suggestion", fontWeight = FontWeight.Bold)
                    Text(
                        text = cleanedText.ifEmpty { "No tracking parameters detected" },
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Button(
                onClick = {
                    clipboardText = ""
                    cleanedText = ""
                    clearClipboard(context)
                    Toast.makeText(context, "Clipboard cleared", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                Text("Clear Clipboard", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(cleanedText))
                    Toast.makeText(context, "Clean link copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = cleanedText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
            ) {
                Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                Text("Copy clean version", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun clearClipboard(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
}

private fun removeTrackingParams(text: String): String {
    if (!text.contains("?")) return text
    val parts = text.split("?", limit = 2)
    val base = parts.first()
    val query = parts.getOrNull(1) ?: return text
    val filtered = query.split("&")
        .filterNot { param ->
            val key = param.substringBefore("=").lowercase()
            key.startsWith("utm_") ||
                    key.contains("fbclid") ||
                    key.contains("gclid") ||
                    key.contains("mc_")
        }
    return if (filtered.isEmpty()) base else "$base?${filtered.joinToString("&")}"
}

