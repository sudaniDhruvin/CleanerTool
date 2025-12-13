package com.example.cleanertool.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.ads.NativeAdView
import com.example.cleanertool.viewmodel.ContactDuplicateGroup
import com.example.cleanertool.viewmodel.ContactsCleanerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsCleanerScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: ContactsCleanerViewModel = viewModel()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var hasPermission by remember { mutableStateOf(isContactsPermissionGranted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.loadDuplicates(context)
        } else {
            Toast.makeText(context, "Contacts permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadDuplicates(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts Cleaner", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (!hasPermission) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("Allow", color = Color(0xFF9C27B0), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF9C27B0))
            )
        }
    ) { paddingValues ->
        when {
            !hasPermission -> PermissionInfo(paddingValues, onRequest = {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            })
            isLoading -> LoadingState("Scanning contacts…")
            error != null -> ErrorState(error ?: "Unknown error")
            duplicateGroups.isEmpty() -> EmptyContactsState()
            else -> Column(modifier = Modifier.fillMaxSize()) {
                DuplicateContactsList(
                    paddingValues = PaddingValues(0.dp),
                    groups = duplicateGroups,
                    onOpenContact = { id ->
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())
                        }
                        ContextCompat.startActivity(context, intent, null)
                    },
                    modifier = Modifier.weight(1f)
                )
                
                // Native Ad
                Spacer(modifier = Modifier.height(16.dp))
                NativeAdView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // Banner Ad
                Spacer(modifier = Modifier.height(8.dp))
                BannerAdView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PermissionInfo(
    paddingValues: PaddingValues,
    onRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Contacts, contentDescription = null, tint = Color(0xFF9C27B0))
        Text("Contacts permission needed", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Grant access so we can surface duplicate entries safely.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
        ) {
            Text("Grant permission", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(message, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Something went wrong", fontWeight = FontWeight.Bold)
        Text(message, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun EmptyContactsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF9C27B0))
        Text("No duplicate contacts detected.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("Your address book already looks clean.", color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DuplicateContactsList(
    paddingValues: PaddingValues,
    groups: List<ContactDuplicateGroup>,
    onOpenContact: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.normalizedKey }) { group ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Number: ${group.entries.first().phone}", fontWeight = FontWeight.Bold)
                    group.entries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = { onOpenContact(entry.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCE93D8))
                            ) {
                                Text("Open", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isContactsPermissionGranted(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PERMISSION_GRANTED
}

