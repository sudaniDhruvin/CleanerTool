package com.example.cleanertool.ui.screens

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.cleanertool.ui.theme.CleanerToolTheme
import com.example.cleanertool.utils.CallDirection

class AfterCallDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phoneNumber = intent.getStringExtra("phoneNumber")
        val callDirection = intent.getStringExtra("callDirection")?.let {
            runCatching { CallDirection.valueOf(it) }.getOrDefault(CallDirection.UNKNOWN)
        } ?: CallDirection.UNKNOWN
        setFinishOnTouchOutside(false)
        setContent {
            CleanerToolTheme {
                AfterCallDialogScreen(
                    phoneNumber = phoneNumber,
                    callDirection = callDirection,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun AfterCallDialogScreen(
    phoneNumber: String?,
    callDirection: CallDirection,
    onDismiss: () -> Unit
) {
    Log.d("AfterCallDialog", "AfterCallDialogScreen: $phoneNumber ${callDirection.name}")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Call Ended") },
        text = {
            Text(
                buildString {
                    append(callDirection.displayLabel() ?: "Recent call")
                    append("\nNumber: ${phoneNumber ?: "Unknown"}")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Scan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
