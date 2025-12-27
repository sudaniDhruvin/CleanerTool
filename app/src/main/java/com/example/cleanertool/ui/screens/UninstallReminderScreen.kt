package com.example.cleanertool.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import android.app.Activity
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.viewmodel.UninstallReminderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallReminderScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: UninstallReminderViewModel = viewModel()
    val unusedApps by viewModel.unusedApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.analyzeUnusedApps(context)
    }

    // Track which package is being uninstalled so we can update UI when result comes back
    var pendingUninstallPackage by remember { mutableStateOf<String?>(null) }

    // Listen for PACKAGE_REMOVED broadcasts so we can react immediately when the system
    // completes an uninstall (more reliable than waiting for an activity result on some devices).
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == Intent.ACTION_PACKAGE_REMOVED) {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    // Update VM and clear pending flag if this was the package we expected
                    viewModel.markAppUninstalled(pkg)
                    if (pendingUninstallPackage == pkg) {
                        Toast.makeText(context, "App uninstalled", Toast.LENGTH_SHORT).show()
                        pendingUninstallPackage = null
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val uninstallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // We don't rely solely on the activity result because some devices/package installer
        // implementations don't return a result (and may log permission errors). Instead,
        // we'll poll for package removal (see LaunchedEffect below).
        // Clearing pendingUninstallPackage is handled in the polling effect when removal is detected or times out.
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uninstall Reminder") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.analyzeUnusedApps(context) }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unused Apps Detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Apps that are rarely used or take up significant space",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Analyzing apps...")
                        }
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(onClick = { viewModel.analyzeUnusedApps(context) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                unusedApps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "No Unused Apps Found",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "All your apps seem to be in use!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "Found ${unusedApps.size} potentially unused apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(unusedApps) { unusedApp ->
                            UnusedAppItem(
                                unusedApp = unusedApp,
                                viewModel = viewModel,
                                context = context,
                                onUninstallRequested = { packageName ->
                                    try {
                                        pendingUninstallPackage = packageName
                                        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                            data = Uri.parse("package:$packageName")
                                            // Add EXTRA_RETURN_RESULT only when we own the permission to request delete results
                                            // (some package installers will attempt to return a result and may log errors
                                            // if the caller doesn't hold REQUEST_DELETE_PACKAGES).
                                            val hasRequestDelete = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.REQUEST_DELETE_PACKAGES
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasRequestDelete) {
                                                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                            }
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        uninstallLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        // Fallback to simple uninstall intent
                                        try {
                                            val fallback = Intent(Intent.ACTION_DELETE).apply {
                                                data = Uri.parse("package:$packageName")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(fallback)
                                        } catch (ex: Exception) {
                                            Toast.makeText(context, "Unable to start uninstall", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }

        // Poll for uninstall completion when a package is pending. This is more reliable across
        // devices than depending on activity result callbacks which some system installers don't return.
        LaunchedEffect(pendingUninstallPackage) {
            val pkg = pendingUninstallPackage
            if (pkg == null) return@LaunchedEffect

            val pm = context.packageManager
            // Give some devices/installer longer time to complete uninstall. Keep polling as a
            // fallback to detect removal when activity results are not delivered.
            val timeoutMs = 45_000L
            val interval = 1000L
            var elapsed = 0L
            var removed = false
            while (elapsed < timeoutMs) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    // Still installed
                } catch (e: PackageManager.NameNotFoundException) {
                    // Package removed
                    removed = true
                    break
                }
                kotlinx.coroutines.delay(interval)
                elapsed += interval
            }

            if (removed) {
                viewModel.markAppUninstalled(pkg)
                Toast.makeText(context, "App uninstalled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Uninstall not completed", Toast.LENGTH_SHORT).show()
            }
            pendingUninstallPackage = null
                    }
                }
            }
        }
    }
}

@Composable
fun UnusedAppItem(
    unusedApp: com.example.cleanertool.viewmodel.UnusedApp,
    viewModel: UninstallReminderViewModel,
    context: android.content.Context,
    onUninstallRequested: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                unusedApp.priority >= 7 -> MaterialTheme.colorScheme.errorContainer
                unusedApp.priority >= 4 -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            unusedApp.appInfo.icon?.let { drawable ->
                val bitmap = remember {
                    try {
                        drawable.toBitmap(128, 128)
                    } catch (e: Exception) {
                        null
                    }
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = unusedApp.appInfo.appName,
                        modifier = Modifier.size(56.dp)
                    )
                } ?: Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = unusedApp.appInfo.appName,
                    modifier = Modifier.size(56.dp)
                )
            } ?: Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = unusedApp.appInfo.appName,
                modifier = Modifier.size(56.dp)
            )

            // App Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = unusedApp.appInfo.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = unusedApp.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Size: ${viewModel.formatFileSize(unusedApp.appInfo.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Priority: ${unusedApp.priority}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

                    // Uninstall Button - delegate to parent via callback
                    IconButton(
                        onClick = {
                            onUninstallRequested(unusedApp.appInfo.packageName)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Uninstall",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
        }
    }
}
