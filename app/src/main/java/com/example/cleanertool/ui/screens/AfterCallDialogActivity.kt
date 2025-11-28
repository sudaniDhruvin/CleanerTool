package com.example.cleanertool.ui.screens

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.cleanertool.ui.theme.CleanerToolTheme

class AfterCallDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phoneNumber = intent.getStringExtra("phoneNumber")
        setContent {
            CleanerToolTheme {
                AfterCallDialogScreen(phoneNumber)
            }
        }
    }
}

@Composable
fun AfterCallDialogScreen(phoneNumber: String?) {
    Log.d("phoneNumber", "AfterCallDialogScreen: " + phoneNumber)
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Call Ended") },
        text = { Text("Number: ${phoneNumber ?: "Unknown"}") },
        confirmButton = { TextButton(onClick = { /* Scan number */ }) { Text("Scan") } },
        dismissButton = { TextButton(onClick = { /* Close dialog */ }) { Text("Close") } }
    )
}
