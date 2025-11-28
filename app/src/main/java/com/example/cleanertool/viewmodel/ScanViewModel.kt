package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

data class UnnecessaryFile(
    val path: String,
    val name: String,
    val size: Long,
    val type: FileType,
    val uri: Uri? = null
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

    private val _scanningPath = MutableStateFlow("")
    val scanningPath: StateFlow<String> = _scanningPath.asStateFlow()

    private val _currentScanningCategory = MutableStateFlow<FileType?>(null)
    val currentScanningCategory: StateFlow<FileType?> = _currentScanningCategory.asStateFlow()

    private val _filesByCategory =
        MutableStateFlow<Map<FileType, List<UnnecessaryFile>>>(emptyMap())
    val filesByCategory: StateFlow<Map<FileType, List<UnnecessaryFile>>> =
        _filesByCategory.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<FileType>>(emptySet())
    val selectedCategories: StateFlow<Set<FileType>> = _selectedCategories.asStateFlow()

    // DEBUG MODE - set to true to see ALL files
    private val DEBUG_SHOW_ALL_FILES = true
    private val DEBUG_MIN_FILE_SIZE = 100 * 1024L // 100 KB

    fun setSelectedCategories(categories: Set<FileType>) {
        _selectedCategories.value = categories
    }

    /**
     * Start full scan. Note: on Android 11+ you will typically need MANAGE_EXTERNAL_STORAGE (All files access)
     * to access many paths. Otherwise MediaStore & SAF will still return many items.
     */
    fun scanDevice(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            _error.value = null
            _scanProgress.value = 0
            _unnecessaryFiles.value = emptyList()
            _totalSize.value = 0L

            try {
                Log.e("ScanViewModel", "========================================")
                Log.e("ScanViewModel", "STARTING AGGRESSIVE MEDIASTORE SCAN")
                Log.e("ScanViewModel", "Android Version: ${Build.VERSION.SDK_INT}")
                Log.e("ScanViewModel", "========================================")

                val allJunkFiles = mutableListOf<UnnecessaryFile>()

                withContext(Dispatchers.IO) {
                    // 1. Scan ALL files via MediaStore.Files (most comprehensive)
                    _scanProgress.value = 10
                    _scanningPath.value = "Scanning all files..."
                    scanAllFilesViaMediaStore(context, allJunkFiles)

                    // 2. Scan Downloads specifically
                    _scanProgress.value = 30
                    _scanningPath.value = "Scanning Downloads..."
                    scanDownloadsViaMediaStore(context, allJunkFiles)

                    // 3. Scan Images for thumbnails
                    _scanProgress.value = 45
                    _scanningPath.value = "Scanning Images..."
                    scanImagesViaMediaStore(context, allJunkFiles)

                    // 4. Scan Videos
                    _scanProgress.value = 55
                    _scanningPath.value = "Scanning Videos..."
                    scanVideosViaMediaStore(context, allJunkFiles)

                    // 5. Scan Audio
                    _scanProgress.value = 65
                    _scanningPath.value = "Scanning Audio..."
                    scanAudioViaMediaStore(context, allJunkFiles)

                    // 6. Scan app-specific storage (always accessible)
                    _scanProgress.value = 75
                    _scanningPath.value = "Scanning App Data..."
                    scanAppStorage(context, allJunkFiles)

                    // 7. Try accessible folders
                    _scanProgress.value = 90
                    _scanningPath.value = "Scanning accessible folders..."
                    scanAccessibleFolders(context, allJunkFiles)

                    _scanProgress.value = 100
                }

                // Remove duplicates by path
                val uniqueFiles = allJunkFiles.distinctBy { it.path }

                _unnecessaryFiles.value = uniqueFiles
                _totalSize.value = uniqueFiles.sumOf { it.size }
                _scanningPath.value = ""
                _currentScanningCategory.value = null
                _filesByCategory.value = uniqueFiles.groupBy { it.type }

                Log.e("ScanViewModel", "")
                Log.e("ScanViewModel", "========================================")
                Log.e("ScanViewModel", "SCAN COMPLETE!")
                Log.e("ScanViewModel", "========================================")
                Log.e("ScanViewModel", "Total files found: ${uniqueFiles.size}")
                Log.e("ScanViewModel", "Total size: ${formatFileSize(_totalSize.value)}")
                Log.e("ScanViewModel", "")
                Log.e("ScanViewModel", "Breakdown by type:")
                _filesByCategory.value.forEach { (type, files) ->
                    Log.e(
                        "ScanViewModel",
                        "  $type: ${files.size} files (${formatFileSize(files.sumOf { it.size })})"
                    )
                    files.take(5).forEach { file ->
                        Log.e("ScanViewModel", "    - ${file.name} (${formatFileSize(file.size)})")
                    }
                }
                Log.e("ScanViewModel", "========================================")

                if (uniqueFiles.isEmpty()) {
                    _error.value = "No junk files found. This is unusual - check app permissions."
                }

            } catch (e: Exception) {
                Log.e("ScanViewModel", "FATAL ERROR: ${e.message}", e)
                e.printStackTrace()
                _error.value = "Scan failed: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    // --------------------------
    // MediaStore: General files
    // --------------------------
    private fun scanAllFilesViaMediaStore(
        context: Context,
        fileList: MutableList<UnnecessaryFile>
    ) {
        val uri = MediaStore.Files.getContentUri("external")

        // Choose projection - avoid DATA on Android 10+ where it may be deprecated/removed
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )
        } else {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )
        }

        try {
            // First, log some sample files to help debugging
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                Log.e("ScanViewModel", "Total files in MediaStore: ${cursor.count}")
                Log.e("ScanViewModel", "")
                Log.e("ScanViewModel", "=== SAMPLE FILES (first 10) ===")

                val idCol = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns._ID)
                val nameCol = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.SIZE)
                val dataCol = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.DATA)
                val relPathCol =
                    getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.RELATIVE_PATH)

                var count = 0
                while (cursor.moveToNext() && count < 10) {
                    val name =
                        if (nameCol >= 0) cursor.getString(nameCol) ?: "unknown" else "unknown"
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val data = when {
                        dataCol >= 0 -> cursor.getString(dataCol)
                        relPathCol >= 0 -> (cursor.getString(relPathCol) ?: "") + name
                        else -> "no path"
                    }
                    Log.e("ScanViewModel", "${count + 1}. $name - ${formatFileSize(size)}")
                    Log.e("ScanViewModel", "   Path: $data")
                    count++
                }
                Log.e("ScanViewModel", "=================================")
                Log.e("ScanViewModel", "")
            }

            // Build selection for likely junk files
            val patterns = listOf(
                "%.apk",
                "%.tmp",
                "%.temp",
                "%temp%",
                "%.bak",
                "%.crdownload",
                "%.part",
                "%.download",
                "%.log",
                ".thumbdata%",
                "%thumbnail%",
                "%cache%"
            )

            // Build SQL: (DISPLAY_NAME LIKE ? OR DISPLAY_NAME LIKE ? OR ...)
            val selection = patterns.joinToString(separator = " OR ") {
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            }.let { "($it)" }

            val selectionArgs = patterns.toTypedArray()

            Log.d("ScanViewModel", "Searching with selection: $selection")
            Log.d("ScanViewModel", "Pattern count: ${selectionArgs.size}")
            Log.e("ScanViewModel", "")

            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                Log.e("ScanViewModel", "Found ${cursor.count} matching files via MediaStore.Files")

                val idColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns._ID)
                val nameColumn =
                    getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.SIZE)
                val dataColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.DATA)
                val relPathColumn =
                    getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.RELATIVE_PATH)

                var addedCount = 0
                while (cursor.moveToNext()) {
                    val id = if (idColumn >= 0) cursor.getLong(idColumn) else -1L
                    val name =
                        if (nameColumn >= 0) cursor.getString(nameColumn) ?: continue else continue
                    val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L

                    // Resolve path: prefer DATA (legacy) else RELATIVE_PATH + DISPLAY_NAME
                    val data = when {
                        dataColumn >= 0 -> cursor.getString(dataColumn)
                        relPathColumn >= 0 -> {
                            val rel = cursor.getString(relPathColumn)
                            if (!rel.isNullOrEmpty()) "$rel$name" else name
                        }

                        else -> name
                    }

                    val itemUri = if (id >= 0) Uri.withAppendedPath(uri, id.toString()) else null

                    val type = categorizeFile(name)
                    if (type != null && size > 0) {
                        fileList.add(
                            UnnecessaryFile(
                                path = data ?: name,
                                name = name,
                                size = size,
                                type = type,
                                uri = itemUri
                            )
                        )
                        addedCount++
                        Log.d("ScanViewModel", "Added: $name (${type}) - ${formatFileSize(size)}")
                    }
                }
                Log.e("ScanViewModel", "Successfully added $addedCount files from MediaStore.Files")

                // DEBUG MODE: If no junk found, show large files as potential cache
                if (DEBUG_SHOW_ALL_FILES && addedCount == 0) {
                    Log.e("ScanViewModel", "")
                    Log.e("ScanViewModel", "=== DEBUG: NO JUNK FOUND, SCANNING LARGE FILES ===")
                    scanLargeFilesForDebug(context, fileList)
                }
            }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error scanning all files: ${e.message}", e)
        }
    }

    // --------------------------
    // Large-file debug scan
    // --------------------------
    private fun scanLargeFilesForDebug(context: Context, fileList: MutableList<UnnecessaryFile>) {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )
        } else {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )
        }

        val selection = "${MediaStore.Files.FileColumns.SIZE} > ?"
        val selectionArgs = arrayOf(DEBUG_MIN_FILE_SIZE.toString())

        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.SIZE} DESC"
            )?.use { cursor ->
                Log.e(
                    "ScanViewModel",
                    "Found ${cursor.count} files > ${formatFileSize(DEBUG_MIN_FILE_SIZE)}"
                )

                val idColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns._ID)
                val nameColumn =
                    getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.SIZE)
                val dataColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.DATA)
                val pathColumn =
                    getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.RELATIVE_PATH)

                var count = 0
                while (cursor.moveToNext() && count < 20) {
                    val id = if (idColumn >= 0) cursor.getLong(idColumn) else -1L
                    val name =
                        if (nameColumn >= 0) cursor.getString(nameColumn) ?: continue else continue
                    val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L
                    val data = when {
                        dataColumn >= 0 -> cursor.getString(dataColumn)
                        pathColumn >= 0 -> (cursor.getString(pathColumn) ?: "") + name
                        else -> name
                    }

                    Log.e("ScanViewModel", "${count + 1}. $name - ${formatFileSize(size)}")
                    Log.e("ScanViewModel", "   Path: ${data ?: "unknown"}")

                    val path = data ?: name
                    val itemUri = if (id >= 0) Uri.withAppendedPath(uri, id.toString()) else null

                    fileList.add(
                        UnnecessaryFile(
                            path = path,
                            name = name,
                            size = size,
                            type = FileType.CACHE,
                            uri = itemUri
                        )
                    )
                    count++
                }
                Log.e("ScanViewModel", "Added ${count} large files as potential cache")
            }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error in debug scan: ${e.message}")
        }
    }

    // --------------------------
    // Downloads collection
    // --------------------------
    private fun scanDownloadsViaMediaStore(
        context: Context,
        fileList: MutableList<UnnecessaryFile>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.d("ScanViewModel", "Skipping Downloads API (requires Android 10+)")
            return
        }

        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATA
        )

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                Log.e("ScanViewModel", "Downloads collection: ${cursor.count} total items")

                val idColumn = getColumnIndexSafe(cursor, MediaStore.Downloads._ID)
                val nameColumn = getColumnIndexSafe(cursor, MediaStore.Downloads.DISPLAY_NAME)
                val sizeColumn = getColumnIndexSafe(cursor, MediaStore.Downloads.SIZE)
                val dataColumn = getColumnIndexSafe(cursor, MediaStore.Downloads.DATA)

                var addedCount = 0
                while (cursor.moveToNext()) {
                    val id = if (idColumn >= 0) cursor.getLong(idColumn) else -1L
                    val name =
                        if (nameColumn >= 0) cursor.getString(nameColumn) ?: continue else continue
                    val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L
                    val data = if (dataColumn >= 0) cursor.getString(dataColumn) else null
                    val itemUri = if (id >= 0) Uri.withAppendedPath(uri, id.toString()) else null

                    val type = categorizeFile(name)
                    if (type != null && size > 0) {
                        // Check if not already added (by path or name)
                        if (!fileList.any { it.path == data || it.name == name }) {
                            fileList.add(
                                UnnecessaryFile(
                                    path = data ?: name,
                                    name = name,
                                    size = size,
                                    type = type,
                                    uri = itemUri
                                )
                            )
                            addedCount++
                            Log.d("ScanViewModel", "Added from Downloads: $name (${type})")
                        }
                    }
                }
                Log.e("ScanViewModel", "Added $addedCount new files from Downloads collection")
            }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error scanning Downloads: ${e.message}", e)
        }
    }

    // --------------------------
    // Images / Thumbnails
    // --------------------------
    private fun scanImagesViaMediaStore(context: Context, fileList: MutableList<UnnecessaryFile>) {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA
        )

        // Look for thumbnail and temp files
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(".thumbdata%", "%.tmp", "%.temp")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    Log.e("ScanViewModel", "Found ${cursor.count} image cache/temp files")
                    processMediaStoreCursor(cursor, uri, fileList, FileType.CACHE)
                }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error scanning images: ${e.message}")
        }
    }

    // --------------------------
    // Videos
    // --------------------------
    private fun scanVideosViaMediaStore(context: Context, fileList: MutableList<UnnecessaryFile>) {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA
        )

        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%.tmp", "%.temp")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    Log.d("ScanViewModel", "Found ${cursor.count} temp video files")
                    processMediaStoreCursor(cursor, uri, fileList, FileType.TEMP)
                }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error scanning videos: ${e.message}")
        }
    }

    // --------------------------
    // Audio
    // --------------------------
    private fun scanAudioViaMediaStore(context: Context, fileList: MutableList<UnnecessaryFile>) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%.tmp", "%.temp")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    Log.d("ScanViewModel", "Found ${cursor.count} temp audio files")
                    processMediaStoreCursor(cursor, uri, fileList, FileType.TEMP)
                }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error scanning audio: ${e.message}")
        }
    }

    // Shared cursor processor for media collections
    private fun processMediaStoreCursor(
        cursor: Cursor,
        baseUri: Uri,
        fileList: MutableList<UnnecessaryFile>,
        defaultType: FileType? = null
    ) {
        val idColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns._ID)
        val nameColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.SIZE)
        val dataColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.DATA)
        val relPathColumn = getColumnIndexSafe(cursor, MediaStore.Files.FileColumns.RELATIVE_PATH)

        while (cursor.moveToNext()) {
            val id = if (idColumn >= 0) cursor.getLong(idColumn) else -1L
            val name = if (nameColumn >= 0) cursor.getString(nameColumn) ?: continue else continue
            val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L
            val data = when {
                dataColumn >= 0 -> cursor.getString(dataColumn)
                relPathColumn >= 0 -> (cursor.getString(relPathColumn) ?: "") + name
                else -> name
            }
            val itemUri = if (id >= 0) Uri.withAppendedPath(baseUri, id.toString()) else null

            if (size > 0 && !fileList.any { it.name == name && it.path == data }) {
                val type = defaultType ?: categorizeFile(name) ?: continue
                fileList.add(
                    UnnecessaryFile(
                        path = data ?: name,
                        name = name,
                        size = size,
                        type = type,
                        uri = itemUri
                    )
                )
            }
        }
    }

    // --------------------------
    // Categorize file by name
    // --------------------------
    private fun categorizeFile(name: String): FileType? {
        val nameLower = name.lowercase()
        return when {
            nameLower.endsWith(".apk") -> FileType.OBSOLETE_APK
            nameLower.endsWith(".tmp") || nameLower.endsWith(".temp") -> FileType.TEMP
            nameLower.endsWith(".bak") || nameLower.endsWith(".crdownload") ||
                    nameLower.endsWith(".part") || nameLower.endsWith(".download") -> FileType.JUNK

            nameLower.endsWith(".log") -> FileType.LOG
            nameLower.startsWith(".thumbdata") || nameLower.contains("cache") || nameLower.contains(
                "thumbnail"
            ) -> FileType.CACHE

            else -> null
        }
    }

    // --------------------------
// Scan app internal/external storage for cache/temp files
// --------------------------
    private fun scanAppStorage(context: Context, fileList: MutableList<UnnecessaryFile>) {
        var totalCacheSize = 0L
        var totalCacheFiles = 0

        try {
            // Internal cache
            context.cacheDir?.let { cacheDir ->
                Log.d("ScanViewModel", "Scanning internal cache: ${cacheDir.absolutePath}")
                val sizeBefore = fileList.size

                scanDirectoryRecursive(cacheDir, fileList, FileType.TEMP, maxDepth = 4)

                val added = fileList.size - sizeBefore
                if (added > 0) {
                    val size = fileList.takeLast(added).sumOf { it.size }
                    totalCacheSize += size
                    totalCacheFiles += added
                    Log.e(
                        "ScanViewModel",
                        " -> Found $added internal cache files (${formatFileSize(size)})"
                    )
                }
            }

            // External cache (many apps use this)
            context.externalCacheDir?.let { extCache ->
                if (extCache.exists()) {
                    Log.d("ScanViewModel", "Scanning external cache: ${extCache.absolutePath}")
                    val sizeBefore = fileList.size

                    scanDirectoryRecursive(extCache, fileList, FileType.TEMP, maxDepth = 4)

                    val added = fileList.size - sizeBefore
                    if (added > 0) {
                        val size = fileList.takeLast(added).sumOf { it.size }
                        totalCacheSize += size
                        totalCacheFiles += added
                        Log.e(
                            "ScanViewModel",
                            " -> Found $added external cache files (${formatFileSize(size)})"
                        )
                    }
                }
            }

            // External files directories, including “/Android/data/<package>/files/”
            context.getExternalFilesDirs(null)?.forEach { filesDir ->
                if (filesDir != null && filesDir.exists()) {
                    listOf("temp", "tmp", "cache", ".cache").forEach { dirName ->
                        val tempDir = File(filesDir, dirName)
                        if (tempDir.exists()) {
                            Log.d("ScanViewModel", "Scanning app temp: ${tempDir.absolutePath}")
                            val sizeBefore = fileList.size

                            scanDirectoryRecursive(tempDir, fileList, FileType.TEMP, maxDepth = 3)

                            val added = fileList.size - sizeBefore
                            if (added > 0) {
                                val size = fileList.takeLast(added).sumOf { it.size }
                                totalCacheSize += size
                                totalCacheFiles += added
                                Log.e(
                                    "ScanViewModel",
                                    "  -> Found $added temp files (${formatFileSize(size)})"
                                )
                            }
                        }
                    }
                }
            }

            if (totalCacheFiles > 0) {
                Log.e("ScanViewModel", "")
                Log.e("ScanViewModel", "=== APP CACHE SUMMARY ===")
                Log.e("ScanViewModel", "Total cache files: $totalCacheFiles")
                Log.e("ScanViewModel", "Total cache size: ${formatFileSize(totalCacheSize)}")
                Log.e("ScanViewModel", "========================")
                Log.e("ScanViewModel", "")
            }

        } catch (e: Exception) {
            Log.e("ScanViewModel", "Error scanning app storage: ${e.message}")
        }
    }

    // --------------------------
// Accessible folders like OBB
// --------------------------
    private fun scanAccessibleFolders(context: Context, fileList: MutableList<UnnecessaryFile>) {
        // Try OBB directory (games store large temporary data)
        try {
            context.obbDirs?.forEach { obbDir ->
                if (obbDir?.exists() == true && obbDir.canRead()) {
                    Log.d("ScanViewModel", "Scanning OBB: ${obbDir.absolutePath}")
                    scanDirectoryRecursive(obbDir, fileList, FileType.JUNK, maxDepth = 2)
                }
            }
        } catch (e: Exception) {
            Log.d("ScanViewModel", "OBB not accessible: ${e.message}")
        }
    }

    // --------------------------
// Recursive directory scanner (with max depth)
// --------------------------
    private fun scanDirectoryRecursive(
        dir: File,
        fileList: MutableList<UnnecessaryFile>,
        type: FileType,
        maxDepth: Int
    ) {
        if (maxDepth <= 0 || !dir.exists() || !dir.canRead()) return

        try {
            var count = 0
            dir.listFiles()?.forEach { file ->
                try {
                    if (file.isDirectory) {
                        scanDirectoryRecursive(file, fileList, type, maxDepth - 1)
                    } else if (file.isFile && file.length() > 0) {
                        // Avoid duplicates
                        if (!fileList.any { it.path == file.absolutePath }) {
                            fileList.add(
                                UnnecessaryFile(
                                    path = file.absolutePath,
                                    name = file.name,
                                    size = file.length(),
                                    type = type
                                )
                            )
                            count++
                        }
                    }
                } catch (_: Exception) {
                }
            }
            if (count > 0) {
                Log.d("ScanViewModel", "Added $count files from: ${dir.name}")
            }
        } catch (_: Exception) {
        }
    }

    // --------------------------
// Human-friendly file size
// --------------------------
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(
                "%.2f GB",
                bytes / (1024.0 * 1024.0 * 1024.0)
            )

            bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    // --------------------------
// Delete / Clean files
// --------------------------
    fun cleanFiles(
        context: Context,
        selectedCategories: Set<FileType>,
        onProgress: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val filesToDelete = _unnecessaryFiles.value
                .filter { selectedCategories.contains(it.type) }

            if (filesToDelete.isEmpty()) {
                onProgress(100)
                return@launch
            }

            var deletedCount = 0
            var deletedSize = 0L
            var failedCount = 0

            val deletedPaths = mutableSetOf<String>()
            val deletedUris = mutableSetOf<Uri>()

            withContext(Dispatchers.IO) {
                filesToDelete.forEachIndexed { index, file ->
                    try {
                        val deleted = if (file.uri != null) {
                            try {
                                val rows = context.contentResolver.delete(file.uri, null, null)
                                rows > 0
                            } catch (se: SecurityException) {
                                false
                            }
                        } else {
                            val fileObj = File(file.path)
                            if (fileObj.exists()) fileObj.delete() else false
                        }

                        if (deleted) {
                            deletedCount++
                            deletedSize += file.size
                            deletedPaths.add(file.path)
                            file.uri?.let { deletedUris.add(it) }
                        } else {
                            failedCount++
                        }
                    } catch (_: Exception) {
                        failedCount++
                    }

                    val progress = ((index + 1) * 100) / filesToDelete.size
                    onProgress(progress)
                }
            }

            // Remove successfully deleted items
            val remainingFiles = _unnecessaryFiles.value.filter { file ->
                if (file.uri != null) !deletedUris.contains(file.uri)
                else !deletedPaths.contains(file.path) && File(file.path).exists()
            }

            _unnecessaryFiles.value = remainingFiles
            _totalSize.value = remainingFiles.sumOf { it.size }
            _filesByCategory.value = remainingFiles.groupBy { it.type }
        }
    }

    // --------------------------
// Safe column index getter
// --------------------------
    private fun getColumnIndexSafe(cursor: Cursor, columnName: String): Int {
        return try {
            cursor.getColumnIndex(columnName)
        } catch (_: Exception) {
            -1
        }
    }

    fun getFilesByCategory(category: FileType): List<UnnecessaryFile> {
        return _filesByCategory.value[category] ?: emptyList()
    }

    fun getTotalSizeByCategory(category: FileType): Long {
        return getFilesByCategory(category).sumOf { it.size }
    }
}