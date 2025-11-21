package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanertool.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UnnecessaryFile(
    val path: String,
    val name: String,
    val size: Long,
    val type: FileType
)

enum class FileType {
    JUNK, OBSOLETE_APK, TEMP, LOG, CACHE
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _unnecessaryFiles = MutableStateFlow<List<UnnecessaryFile>>(emptyList())
    val unnecessaryFiles: StateFlow<List<UnnecessaryFile>> = _unnecessaryFiles.asStateFlow()

    private val _totalSize = MutableStateFlow(0L)
    val totalSize: StateFlow<Long> = _totalSize.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _scanningPath = MutableStateFlow<String>("")
    val scanningPath: StateFlow<String> = _scanningPath.asStateFlow()

    private val _currentScanningCategory = MutableStateFlow<FileType?>(null)
    val currentScanningCategory: StateFlow<FileType?> = _currentScanningCategory.asStateFlow()

    private val _filesByCategory = MutableStateFlow<Map<FileType, List<UnnecessaryFile>>>(emptyMap())
    val filesByCategory: StateFlow<Map<FileType, List<UnnecessaryFile>>> = _filesByCategory.asStateFlow()

    fun scanDevice(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            _error.value = null
            _scanProgress.value = 0
            _unnecessaryFiles.value = emptyList()
            _totalSize.value = 0L

            try {
                val filesByType = mutableMapOf<FileType, MutableList<UnnecessaryFile>>()
                filesByType[FileType.JUNK] = mutableListOf()
                filesByType[FileType.OBSOLETE_APK] = mutableListOf()
                filesByType[FileType.TEMP] = mutableListOf()
                filesByType[FileType.LOG] = mutableListOf()

                // Step 1: Scan junk files (0-25%)
                _scanProgress.value = 0
                _currentScanningCategory.value = FileType.JUNK
                _scanningPath.value = "Scanning:/storage/emulated/0/Android/data/com.miu..."
                val junkFiles = withContext(Dispatchers.IO) { scanJunkFiles(context) }
                filesByType[FileType.JUNK]?.addAll(junkFiles)
                _scanProgress.value = 25

                // Step 2: Scan obsolete APK files (25-50%)
                _currentScanningCategory.value = FileType.OBSOLETE_APK
                _scanningPath.value = "Scanning:/storage/emulated/0/Download..."
                val obsoleteApkFiles = withContext(Dispatchers.IO) { scanObsoleteApkFiles(context) }
                filesByType[FileType.OBSOLETE_APK]?.addAll(obsoleteApkFiles)
                _scanProgress.value = 50

                // Step 3: Scan temp files (50-75%)
                _currentScanningCategory.value = FileType.TEMP
                _scanningPath.value = "Scanning:/storage/emulated/0/Android/data..."
                val tempFiles = withContext(Dispatchers.IO) { scanTempFiles(context) }
                filesByType[FileType.TEMP]?.addAll(tempFiles)
                _scanProgress.value = 75

                // Step 4: Scan log files (75-100%)
                _currentScanningCategory.value = FileType.LOG
                _scanningPath.value = "Scanning:/storage/emulated/0/Android/logs..."
                val logFiles = withContext(Dispatchers.IO) { scanLogFiles(context) }
                filesByType[FileType.LOG]?.addAll(logFiles)
                _scanProgress.value = 100
                _scanningPath.value = ""
                _currentScanningCategory.value = null

                // Combine all files
                val allFiles = filesByType.values.flatten()
                _unnecessaryFiles.value = allFiles
                _totalSize.value = allFiles.sumOf { it.size }
                _filesByCategory.value = filesByType.mapValues { it.value.toList() }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to scan device"
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun scanJunkFiles(context: Context): List<UnnecessaryFile> {
        val junkFiles = mutableListOf<UnnecessaryFile>()
        try {
            // Scan app cache directories
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                scanDirectory(cacheDir, junkFiles, FileType.JUNK)
            }
            
            val externalCacheDir = context.externalCacheDir
            if (externalCacheDir?.exists() == true) {
                scanDirectory(externalCacheDir, junkFiles, FileType.JUNK)
            }

            // Scan Android/data cache directories
            val androidDataDir = File(Environment.getExternalStorageDirectory(), "Android/data")
            if (androidDataDir.exists() && androidDataDir.canRead()) {
                androidDataDir.listFiles()?.forEach { appDir ->
                    val cacheDir = File(appDir, "cache")
                    if (cacheDir.exists() && cacheDir.canRead()) {
                        scanDirectory(cacheDir, junkFiles, FileType.JUNK)
                    }
                }
            }

            // Scan Android/obb cache (though obb files shouldn't be deleted)
            // We'll skip obb files as they're important for apps
        } catch (e: Exception) {
            // Handle error silently
        }
        return junkFiles
    }

    private suspend fun scanObsoleteApkFiles(context: Context): List<UnnecessaryFile> {
        val obsoleteApkFiles = mutableListOf<UnnecessaryFile>()
        try {
            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            val installedPackageNames = installedPackages.map { it.packageName }.toSet()

            // Scan Downloads directory for APK files
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists() && downloadsDir.canRead()) {
                scanDirectoryForApkFiles(downloadsDir, obsoleteApkFiles, installedPackageNames)
            }

            // Also check other common locations
            val externalStorage = Environment.getExternalStorageDirectory()
            val commonDirs = listOf(
                File(externalStorage, "Download"),
                File(externalStorage, "Downloads"),
                File(externalStorage, "APK")
            )

            commonDirs.forEach { dir ->
                if (dir.exists() && dir.canRead()) {
                    scanDirectoryForApkFiles(dir, obsoleteApkFiles, installedPackageNames)
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
        return obsoleteApkFiles
    }

    private suspend fun scanTempFiles(context: Context): List<UnnecessaryFile> {
        val tempFiles = mutableListOf<UnnecessaryFile>()
        try {
            // Scan app temp directories
            val tempDir = File(context.filesDir.parent, "temp")
            if (tempDir.exists()) {
                scanDirectory(tempDir, tempFiles, FileType.TEMP)
            }

            // Scan common temp file locations
            val tempExtensions = listOf(".tmp", ".temp", ".bak", ".swp", "~")
            val externalStorage = Environment.getExternalStorageDirectory()
            
            // Scan Downloads for temp files
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists() && downloadsDir.canRead()) {
                scanDirectoryForTempFiles(downloadsDir, tempFiles, tempExtensions)
            }

            // Scan Android/data temp files
            val androidDataDir = File(Environment.getExternalStorageDirectory(), "Android/data")
            if (androidDataDir.exists() && androidDataDir.canRead()) {
                androidDataDir.listFiles()?.forEach { appDir ->
                    val filesDir = File(appDir, "files")
                    if (filesDir.exists() && filesDir.canRead()) {
                        scanDirectoryForTempFiles(filesDir, tempFiles, tempExtensions)
                    }
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
        return tempFiles
    }

    private suspend fun scanLogFiles(context: Context): List<UnnecessaryFile> {
        val logFiles = mutableListOf<UnnecessaryFile>()
        try {
            val logExtensions = listOf(".log", ".txt")
            
            // Scan app log directories
            val logDir = File(context.filesDir, "logs")
            if (logDir.exists()) {
                scanDirectoryForLogFiles(logDir, logFiles)
            }

            // Scan Android/data for log files
            val androidDataDir = File(Environment.getExternalStorageDirectory(), "Android/data")
            if (androidDataDir.exists() && androidDataDir.canRead()) {
                androidDataDir.listFiles()?.forEach { appDir ->
                    val filesDir = File(appDir, "files")
                    if (filesDir.exists() && filesDir.canRead()) {
                        scanDirectoryForLogFiles(filesDir, logFiles)
                    }
                }
            }

            // Scan common log locations
            val commonLogDirs = listOf(
                File(Environment.getExternalStorageDirectory(), "logs"),
                File(Environment.getExternalStorageDirectory(), "log"),
                File(Environment.getExternalStorageDirectory(), "Android/logs")
            )

            commonLogDirs.forEach { dir ->
                if (dir.exists() && dir.canRead()) {
                    scanDirectoryForLogFiles(dir, logFiles)
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
        return logFiles
    }

    private fun scanDirectory(dir: File, fileList: MutableList<UnnecessaryFile>, type: FileType) {
        try {
            if (!dir.exists() || !dir.canRead()) return
            
            dir.listFiles()?.forEach { file ->
                try {
                    if (file.isDirectory) {
                        scanDirectory(file, fileList, type)
                    } else if (file.canRead()) {
                        val size = file.length()
                        if (size > 0) { // Only add non-empty files
                            fileList.add(
                                UnnecessaryFile(
                                    path = file.absolutePath,
                                    name = file.name,
                                    size = size,
                                    type = type
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be accessed
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun scanDirectoryForApkFiles(
        dir: File,
        fileList: MutableList<UnnecessaryFile>,
        installedPackageNames: Set<String>
    ) {
        try {
            if (!dir.exists() || !dir.canRead()) return
            
            dir.listFiles()?.forEach { file ->
                try {
                    if (file.isDirectory) {
                        scanDirectoryForApkFiles(file, fileList, installedPackageNames)
                    } else if (file.name.endsWith(".apk", ignoreCase = true) && file.canRead()) {
                        // Check if this APK is for an installed package
                        val apkName = file.name.lowercase()
                        val isInstalled = installedPackageNames.any { packageName ->
                            apkName.contains(packageName.lowercase()) || 
                            apkName.contains(packageName.replace(".", "_").lowercase())
                        }
                        
                        // If APK is for an installed app, it's obsolete (already installed)
                        if (isInstalled || file.name.contains("base.apk", ignoreCase = true)) {
                            fileList.add(
                                UnnecessaryFile(
                                    path = file.absolutePath,
                                    name = file.name,
                                    size = file.length(),
                                    type = FileType.OBSOLETE_APK
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be accessed
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun scanDirectoryForTempFiles(
        dir: File,
        fileList: MutableList<UnnecessaryFile>,
        tempExtensions: List<String>
    ) {
        try {
            if (!dir.exists() || !dir.canRead()) return
            
            dir.listFiles()?.forEach { file ->
                try {
                    if (file.isDirectory) {
                        scanDirectoryForTempFiles(file, fileList, tempExtensions)
                    } else if (file.canRead()) {
                        val fileName = file.name.lowercase()
                        val isTempFile = tempExtensions.any { ext -> fileName.endsWith(ext) } ||
                                fileName.contains("temp") || fileName.contains("tmp")
                        
                        if (isTempFile) {
                            fileList.add(
                                UnnecessaryFile(
                                    path = file.absolutePath,
                                    name = file.name,
                                    size = file.length(),
                                    type = FileType.TEMP
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be accessed
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun scanDirectoryForLogFiles(dir: File, fileList: MutableList<UnnecessaryFile>) {
        try {
            if (!dir.exists() || !dir.canRead()) return
            
            dir.listFiles()?.forEach { file ->
                try {
                    if (file.isDirectory) {
                        scanDirectoryForLogFiles(file, fileList)
                    } else if (file.canRead() && file.name.endsWith(".log", ignoreCase = true)) {
                        fileList.add(
                            UnnecessaryFile(
                                path = file.absolutePath,
                                name = file.name,
                                size = file.length(),
                                type = FileType.LOG
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip files that can't be accessed
                }
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun cleanFiles(
        context: Context,
        selectedCategories: Set<FileType>,
        onProgress: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            // Filter files by selected categories
            val filesToDelete = _unnecessaryFiles.value
                .filter { selectedCategories.contains(it.type) }
                .toList()
            
            if (filesToDelete.isEmpty()) {
                onProgress(100)
                return@launch
            }

            var deletedCount = 0
            var deletedSize = 0L
            var failedCount = 0

            withContext(Dispatchers.IO) {
                filesToDelete.forEachIndexed { index, file ->
                    try {
                        val fileObj = File(file.path)
                        // Validate file exists and is writable before deletion
                        if (fileObj.exists() && fileObj.canWrite()) {
                            val size = fileObj.length()
                            if (fileObj.delete()) {
                                deletedCount++
                                deletedSize += size
                            } else {
                                failedCount++
                            }
                        } else {
                            failedCount++
                        }
                    } catch (e: SecurityException) {
                        // Permission denied
                        failedCount++
                    } catch (e: Exception) {
                        // Other errors
                        failedCount++
                    }
                    
                    // Update progress
                    val progress = ((index + 1) * 100) / filesToDelete.size
                    onProgress(progress)
                }
            }

            // Update the list to remove deleted files
            val remainingFiles = _unnecessaryFiles.value.filter { file ->
                val fileObj = File(file.path)
                !fileObj.exists() || !selectedCategories.contains(file.type)
            }
            
            _unnecessaryFiles.value = remainingFiles
            _totalSize.value = remainingFiles.sumOf { it.size }
            
            // Update files by category
            val updatedFilesByCategory = _filesByCategory.value.toMutableMap()
            selectedCategories.forEach { category ->
                updatedFilesByCategory[category] = updatedFilesByCategory[category]
                    ?.filter { !File(it.path).exists() } ?: emptyList()
            }
            _filesByCategory.value = updatedFilesByCategory
        }
    }

    fun getFilesByCategory(category: FileType): List<UnnecessaryFile> {
        return _filesByCategory.value[category] ?: emptyList()
    }

    fun getTotalSizeByCategory(category: FileType): Long {
        return getFilesByCategory(category).sumOf { it.size }
    }
}

