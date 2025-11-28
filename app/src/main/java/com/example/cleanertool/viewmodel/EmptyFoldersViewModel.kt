package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EmptyFolder(
    val path: String,
    val parent: String
)

class EmptyFoldersViewModel(application: Application) : AndroidViewModel(application) {

    private val _emptyFolders = MutableStateFlow<List<EmptyFolder>>(emptyList())
    val emptyFolders: StateFlow<List<EmptyFolder>> = _emptyFolders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun scanEmptyFolders(context: Context = getApplication()) {
        viewModelScope.launch {
            _isScanning.value = true
            _error.value = null
            try {
                val folders = withContext(Dispatchers.IO) {
                    val result = mutableListOf<EmptyFolder>()
                    val roots = gatherRootDirectories(context)
                    roots.forEach { root ->
                        scanDirectory(root, result, depth = 0)
                    }
                    result.distinctBy { it.path }
                }
                _emptyFolders.value = folders
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to scan folders"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun deleteFolders(
        context: Context = getApplication(),
        paths: Set<String>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                paths.forEach { path ->
                    runCatching { File(path).deleteRecursively() }
                }
            }
            val remaining = _emptyFolders.value.filterNot { paths.contains(it.path) }
            _emptyFolders.value = remaining
            onComplete()
        }
    }

    private fun gatherRootDirectories(context: Context): List<File> {
        val roots = mutableSetOf<File>()
        listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.externalCacheDir
        ).forEach { roots.add(it) }

        context.getExternalFilesDirs(null)?.forEach { dir ->
            dir?.let { roots.add(it) }
        }

        context.externalMediaDirs?.forEach { dir ->
            dir?.let { roots.add(it) }
        }

        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads?.exists() == true) {
            roots.add(downloads)
        }

        return roots.filter { it.exists() && it.isDirectory }
    }

    private fun scanDirectory(
        dir: File,
        collector: MutableList<EmptyFolder>,
        depth: Int,
        maxDepth: Int = 6
    ) {
        if (depth > maxDepth || !dir.exists() || !dir.isDirectory || !dir.canRead()) return
        val children = dir.listFiles() ?: return
        if (children.isEmpty()) {
            collector.add(EmptyFolder(path = dir.absolutePath, parent = dir.parent ?: ""))
        } else {
            children.filter { it.isDirectory }.forEach { child ->
                scanDirectory(child, collector, depth + 1, maxDepth)
            }
        }
    }
}

