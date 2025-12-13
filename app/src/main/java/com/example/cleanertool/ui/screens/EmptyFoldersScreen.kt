package com.example.cleanertool.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cleanertool.ads.BannerAdView
import com.example.cleanertool.ads.NativeAdView
import com.example.cleanertool.viewmodel.EmptyFolder
import com.example.cleanertool.viewmodel.EmptyFoldersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyFoldersScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: EmptyFoldersViewModel = viewModel()
    val folders by viewModel.emptyFolders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val error by viewModel.error.collectAsState()

    var selected by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        viewModel.scanEmptyFolders(context)
    }

    LaunchedEffect(folders) {
        selected = folders.map { it.path }.toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Empty Folders", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF009688))
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Button(
                    onClick = {
                        viewModel.deleteFolders(context, selected) {
                            Toast.makeText(context, "Folders removed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009688))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                    Text("Delete selected (${selected.size})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        when {
            isScanning -> LoadingIndicator(message = "Looking for empty folders…")
            error != null -> ErrorState(message = error ?: "Unknown error")
            folders.isEmpty() -> EmptyState()
            else -> Column(modifier = Modifier.fillMaxSize()) {
                FolderList(
                    paddingValues = PaddingValues(0.dp),
                    folders = folders,
                    selected = selected,
                    onToggle = { path ->
                        selected = if (selected.contains(path)) selected - path else selected + path
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
private fun FolderList(
    paddingValues: PaddingValues,
    folders: List<EmptyFolder>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(folders, key = { it.path }) { folder ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.path.substringAfterLast("/"), fontWeight = FontWeight.Bold)
                        Text(folder.parent, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Checkbox(
                        checked = selected.contains(folder.path),
                        onCheckedChange = { onToggle(folder.path) },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF009688))
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(message: String) {
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
        Text("Unable to scan", fontWeight = FontWeight.Bold)
        Text(message, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No empty folders detected.", fontWeight = FontWeight.Bold)
        Text("Your storage is neatly organized.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    }
}

