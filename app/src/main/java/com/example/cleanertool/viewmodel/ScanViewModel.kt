package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.content.ContentUris
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanertool.utils.storage.DirectoryScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import androidx.documentfile.provider.DocumentFile

data class UnnecessaryFile(
    val path: String,
    val name: String,
    val size: Long,
    val type: FileType,
    val uri: Uri? = null
)

enum class FileType {
    JUNK,
    OBSOLETE_APK,
    TEMP,
    LOG,
    CACHE,
    IMAGE,
    VIDEO,
    DOCUMENT
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ScanViewModel"

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

    // Emitted when deletion requires user confirmation (MediaStore delete request)
    private val _pendingDeleteUris = kotlinx.coroutines.flow.MutableSharedFlow<List<Uri>>(extraBufferCapacity = 1)
    val pendingDeleteUris = _pendingDeleteUris.asSharedFlow()

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
                    runScanStage(FileType.JUNK, 10, "Scanning all files...") {
                        scanAllFilesViaMediaStore(context, allJunkFiles)
                    }

                    runScanStage(FileType.JUNK, 25, "Scanning Downloads...") {
                        scanDownloadsViaMediaStore(context, allJunkFiles)
                    }

                    // Scan for documents (pdf/docx/xlsx/txt/zip/etc.) across MediaStore Files
                    runScanStage(FileType.DOCUMENT, 40, "Scanning Documents...") {
                        scanDocumentsViaMediaStore(context, allJunkFiles)
                    }

                    runScanStage(FileType.IMAGE, 55, "Scanning Images...") {
                        scanImagesViaMediaStore(context, allJunkFiles)
                    }

                    runScanStage(FileType.VIDEO, 70, "Scanning Videos...") {
                        scanVideosViaMediaStore(context, allJunkFiles)
                    }

                    runScanStage(FileType.TEMP, 80, "Scanning Audio...") {
                        scanAudioViaMediaStore(context, allJunkFiles)
                    }

                    runScanStage(FileType.TEMP, 80, "Scanning App Data...") {
                        scanAppStorage(context, allJunkFiles)
                    }

                    runScanStage(FileType.JUNK, 90, "Scanning accessible folders...") {
                        scanAccessibleFolders(context, allJunkFiles)
                    }

                    runScanStage(FileType.JUNK, 100, "Scanning storage tree...") {
                        scanExternalStorageTree(context, allJunkFiles)
                    }
                }

                _scanProgress.value = 100

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

    private suspend fun runScanStage(
        category: FileType?,
        progress: Int,
        statusMessage: String,
        block: suspend () -> Unit
    ) {
        _currentScanningCategory.value = category
        _scanningPath.value = statusMessage
        try {
            block()
        } catch (e: Exception) {
            Log.e("ScanViewModel", "Stage failed: $statusMessage -> ${e.message}")
        }
        _scanProgress.value = progress
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
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Error scanning all files: ${t.message}", t)
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
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Error in debug scan: ${t.message}", t)
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
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Error scanning Downloads: ${t.message}", t)
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
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE
        )
        // Collect all images available via MediaStore (user requested: show all photos)
        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")
                ?.use { cursor ->
                    Log.e("ScanViewModel", "Found ${cursor.count} images in MediaStore")
                    processMediaStoreCursor(cursor, uri, fileList, FileType.IMAGE)
                }
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Error scanning images: ${t.message}", t)
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
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE
        )
        // Collect all videos available via MediaStore
        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")
                ?.use { cursor ->
                    Log.e("ScanViewModel", "Found ${cursor.count} videos in MediaStore")
                    processMediaStoreCursor(cursor, uri, fileList, FileType.VIDEO)
                }
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Error scanning videos: ${t.message}", t)
        }
    }

    // --------------------------
    // Documents (PDF/DOC/TXT/ZIP/...)
    // --------------------------
    private fun scanDocumentsViaMediaStore(context: Context, fileList: MutableList<UnnecessaryFile>) {
        val uri = MediaStore.Files.getContentUri("external")

        // Find common document and archive extensions by name
        val patterns = listOf(
            "%.pdf", "%.doc", "%.docx", "%.ppt", "%.pptx", "%.xls", "%.xlsx", "%.txt", "%.rtf", "%.odt", "%.zip", "%.rar", "%.7z", "%.tar", "%.gz"
        )

        val selection = patterns.joinToString(separator = " OR ") { "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" }
        val selectionArgs = patterns.toTypedArray()

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
                MediaStore.Files.FileColumns.MIME_TYPE
            )
        }

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
                ?.use { cursor ->
                    Log.e("ScanViewModel", "Documents matched: ${cursor.count}")
                    processMediaStoreCursor(cursor, uri, fileList, FileType.DOCUMENT)
                }
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Error scanning documents: ${t.message}", t)
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
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Error scanning audio: ${t.message}", t)
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
            // Images
                nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") ||
                nameLower.endsWith(".gif") || nameLower.endsWith(".webp") || nameLower.endsWith(".heic") || nameLower.endsWith(".heif") -> FileType.IMAGE

            // Videos
            nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".mov") ||
                nameLower.endsWith(".avi") || nameLower.endsWith(".3gp") || nameLower.endsWith(".webm") -> FileType.VIDEO

            // Documents / Archives
            nameLower.endsWith(".pdf") || nameLower.endsWith(".doc") || nameLower.endsWith(".docx") ||
                nameLower.endsWith(".ppt") || nameLower.endsWith(".pptx") || nameLower.endsWith(".xls") ||
                nameLower.endsWith(".xlsx") || nameLower.endsWith(".txt") || nameLower.endsWith(".rtf") ||
                nameLower.endsWith(".zip") || nameLower.endsWith(".rar") -> FileType.DOCUMENT

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

    private suspend fun scanExternalStorageTree(
        context: Context,
        fileList: MutableList<UnnecessaryFile>
    ) {
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists()) {
            Log.d("ScanViewModel", "External storage root missing")
            return
        }

        val trashDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Trash")

        DirectoryScanner.scan(
            root = root,
            showHidden = false,
            skipDir = { dir ->
                !dir.canRead() ||
                        dir.absolutePath.startsWith(trashDir.absolutePath)
            },
            onLockedDir = { locked ->
                Log.d("ScanViewModel", "Skipping protected dir: ${locked.absolutePath}")
            }
        ) { file ->
            val type = categorizeFile(file.name) ?: return@scan
            if (file.length() <= 0) return@scan
            val path = file.absolutePath

            if (!fileList.any { it.path == path }) {
                fileList.add(
                    UnnecessaryFile(
                        path = path,
                        name = file.name,
                        size = file.length(),
                        type = type
                    )
                )
            }
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
        val filesToDelete = _unnecessaryFiles.value
            .filter { selectedCategories.contains(it.type) }
        deleteSpecificFiles(context, filesToDelete, onProgress)
    }

    fun deleteSpecificFiles(
        context: Context,
        files: List<UnnecessaryFile>,
        onProgress: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            performDeletion(context, files, onProgress)
        }
    }

    private suspend fun performDeletion(
        context: Context,
        filesToDelete: List<UnnecessaryFile>,
        onProgress: (Int) -> Unit
    ) {
        if (filesToDelete.isEmpty()) {
            withContext(Dispatchers.Main) { onProgress(100) }
            return
        }

        var deletedCount = 0
        var deletedSize = 0L
        var failedCount = 0

        val deletedPaths = mutableSetOf<String>()
        val deletedUris = mutableSetOf<Uri>()
        val pendingUserDeleteUris = mutableSetOf<Uri>()

        // Flow to notify UI when some URIs require user confirmation to delete
        // (e.g., scoped storage on Android 11+)
        // _pendingDeleteUris is defined below in the ViewModel scope

        withContext(Dispatchers.IO) {
            filesToDelete.forEachIndexed { index, file ->
                try {
                    val deleted = if (file.uri != null) {
                        try {
                            val rows = context.contentResolver.delete(file.uri, null, null)
                            rows > 0
                        } catch (se: SecurityException) {
                            // Collect URIs that require user consent so UI can prompt
                            file.uri?.let {
                                pendingUserDeleteUris.add(it)
                                Log.w(TAG, "SecurityException deleting uri: $it", se)
                            }
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

                        // Ensure MediaStore / system index is updated so Gallery / Files app reflect deletion
                        try {
                            // If we have a path, remove MediaStore entries referencing it and trigger a rescan of parent dir
                            val resolvedPath = file.path.ifBlank { getPathFromUri(context, file.uri) ?: "" }
                            if (resolvedPath.isNotBlank()) {
                                purgeMediaStoreEntryForPath(context, resolvedPath)
                                val parent = java.io.File(resolvedPath).parent ?: resolvedPath
                                MediaScannerConnection.scanFile(context, arrayOf(parent), null, null)
                            } else if (file.uri != null) {
                                // If we only have URI, attempt to resolve the path and rescan
                                val p = getPathFromUri(context, file.uri)
                                if (!p.isNullOrBlank()) {
                                    purgeMediaStoreEntryForPath(context, p)
                                    val parent = java.io.File(p).parent ?: p
                                    MediaScannerConnection.scanFile(context, arrayOf(parent), null, null)
                                }
                            }
                        } catch (t: Throwable) {
                            android.util.Log.w(TAG, "Failed to refresh media DB for deleted file: ${file.path} / ${file.uri}", t)
                        }
                    } else {
                        failedCount++
                    }
                } catch (_: Exception) {
                    failedCount++
                }

                val progress = ((index + 1) * 100) / filesToDelete.size
                // Ensure progress callback runs on the main thread so UI can safely show toasts/snackbars
                withContext(Dispatchers.Main) { onProgress(progress) }
            }
        }

        val remainingFiles = _unnecessaryFiles.value.filter { file ->
            if (file.uri != null) !deletedUris.contains(file.uri)
            else !deletedPaths.contains(file.path) && File(file.path).exists()
        }

        _unnecessaryFiles.value = remainingFiles
        _totalSize.value = remainingFiles.sumOf { it.size }
        _filesByCategory.value = remainingFiles.groupBy { it.type }

        if (pendingUserDeleteUris.isNotEmpty()) {
            // Notify UI that user confirmation is required for these URIs
            viewModelScope.launch(Dispatchers.Main) {
                Log.d(TAG, "Emitting pending delete URIs: $pendingUserDeleteUris")
                _pendingDeleteUris.emit(pendingUserDeleteUris.toList())
            }
        }
    }

    fun getDuplicateGroups(): List<List<UnnecessaryFile>> {
        return _unnecessaryFiles.value
            .groupBy { "${it.name.lowercase()}_${it.size}" }
            .values
            .filter { it.size > 1 }
            .sortedByDescending { group -> group.sumOf { file -> file.size } }
    }

    fun cleanDuplicateGroups(
        context: Context,
        onProgress: (Int) -> Unit = {}
    ) {
        val duplicates = getDuplicateGroups()
        val filesToDelete = duplicates.flatMap { group -> group.drop(1) }
        deleteSpecificFiles(context, filesToDelete, onProgress)
    }

    fun getLargeFiles(minSizeBytes: Long = 50L * 1024L * 1024L): List<UnnecessaryFile> {
        return _unnecessaryFiles.value
            .filter { it.size >= minSizeBytes }
            .sortedByDescending { it.size }
    }

    /**
     * Attempt deletion on older OS versions where IntentSender delete flow is not available.
     * This tries to resolve a file path from the URI and delete via File APIs when possible.
     * Returns the list of URIs that still couldn't be removed.
     */
    suspend fun attemptLegacyDeletion(context: Context, uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val remaining = mutableListOf<Uri>()

        uris.forEach { uri ->
            try {
                // Try to resolve file path using MediaStore query
                var path: String? = null
                try {
                    val projection = arrayOf(MediaStore.MediaColumns.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val idx = getColumnIndexSafe(cursor, MediaStore.MediaColumns.DATA)
                        if (idx >= 0 && cursor.moveToFirst()) {
                            path = cursor.getString(idx)
                        }
                    }
                } catch (t: Throwable) {
                    // Ignore provider errors while trying to resolve path
                    android.util.Log.w("ScanViewModel", "Failed to resolve file path for $uri: ${t.message}", t)
                }

                var deleted = false
                if (!path.isNullOrEmpty()) {
                    try {
                        val f = File(path)
                        if (f.exists()) {
                            deleted = f.delete()
                            if (deleted) {
                                android.util.Log.d("ScanViewModel", "Legacy-delete succeeded for $path")
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.w("ScanViewModel", "File delete failed for $path: ${t.message}", t)
                    }
                }

                if (!deleted) {
                    // Last resort: try contentResolver.delete again (some older devices allow it)
                    try {
                        val rows = context.contentResolver.delete(uri, null, null)
                        deleted = rows > 0
                        if (deleted) android.util.Log.d("ScanViewModel", "ContentResolver.delete succeeded for $uri (legacy)")
                        // If we successfully removed via contentResolver, try to refresh media DB
                        if (deleted) {
                            try {
                                val p = getPathFromUri(context, uri)
                                if (!p.isNullOrBlank()) {
                                    purgeMediaStoreEntryForPath(context, p)
                                    val parent = java.io.File(p).parent ?: p
                                    MediaScannerConnection.scanFile(context, arrayOf(parent), null, null)
                                }
                            } catch (_: Throwable) {
                                // ignore
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.w("ScanViewModel", "ContentResolver.delete failed for $uri: ${t.message}", t)
                    }
                }

                if (!deleted) remaining.add(uri)
            } catch (t: Throwable) {
                android.util.Log.w("ScanViewModel", "Unhandled error attempting legacy delete for $uri: ${t.message}", t)
                remaining.add(uri)
            }
        }

        // Refresh internal state: remove any files that were removed via file deletions above
        val currentUris = remaining.toSet()
        val remainingFiles = _unnecessaryFiles.value.filter { file ->
            if (file.uri != null) currentUris.contains(file.uri)
            else File(file.path).exists()
        }

        // If files were removed by File.delete above, also attempt to purge MediaStore entries and rescan directories
        try {
            val removedPaths = _unnecessaryFiles.value.mapNotNull { f ->
                val exists = if (f.uri != null) {
                    // try resolve
                    val p = getPathFromUri(context, f.uri)
                    p
                } else {
                    if (!File(f.path).exists()) f.path else null
                }
                exists
            }.filterNotNull()
            removedPaths.forEach { p ->
                try {
                    purgeMediaStoreEntryForPath(context, p)
                    MediaScannerConnection.scanFile(context, arrayOf(java.io.File(p).parent ?: p), null, null)
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }

        _unnecessaryFiles.value = remainingFiles
        _totalSize.value = remainingFiles.sumOf { it.size }
        _filesByCategory.value = remainingFiles.groupBy { it.type }

        return@withContext remaining
    }

    /**
     * Attempt deletion using a persisted SAF tree permission. This will search the selected
     * folder tree for files matching the display name (and size when possible) and delete them.
     * Returns list of URIs that still couldn't be deleted.
     */
    suspend fun attemptSafDeletionWithTree(context: Context, treeUri: Uri, uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val remaining = mutableListOf<Uri>()

        val root = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Invalid tree uri: $treeUri", t)
            null
        }

        if (root == null) {
            // nothing we can do
            return@withContext uris.toList()
        }

        // Helper to find a document by name and optionally size
        fun findDocByNameAndSize(start: DocumentFile, name: String, size: Long?): DocumentFile? {
            try {
                val stack = ArrayDeque<DocumentFile>()
                stack.add(start)
                while (stack.isNotEmpty()) {
                    val cur = stack.removeFirst()
                    cur.listFiles().forEach { child ->
                        if (child.isDirectory) stack.add(child)
                        else {
                            val matchesName = child.name == name
                            val matchesSize = size == null || try { child.length() == size } catch (_: Throwable) { false }
                            if (matchesName && matchesSize) return child
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Error searching tree for $name", t)
            }
            return null
        }

        uris.forEach { uri ->
            val displayName = try {
                var dn: String? = null
                val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val nameIdx = getColumnIndexSafe(cursor, MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = getColumnIndexSafe(cursor, MediaStore.MediaColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        dn = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    }
                }
                dn ?: uri.lastPathSegment ?: uri.toString()
            } catch (t: Throwable) {
                uri.lastPathSegment ?: uri.toString()
            }

            val size = try {
                var s: Long? = null
                val projection = arrayOf(MediaStore.MediaColumns.SIZE)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val sizeIdx = getColumnIndexSafe(cursor, MediaStore.MediaColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIdx >= 0) s = cursor.getLong(sizeIdx)
                }
                s
            } catch (_: Throwable) { null }

            val found = findDocByNameAndSize(root, displayName ?: uri.lastPathSegment ?: "", size)
            if (found != null) {
                try {
                    val deleted = found.delete()
                    if (deleted) {
                        // Purge / rescan
                        val path = getPathFromUri(context, uri)
                        if (!path.isNullOrBlank()) {
                            purgeMediaStoreEntryForPath(context, path)
                            MediaScannerConnection.scanFile(context, arrayOf(java.io.File(path).parent ?: path), null, null)
                        }
                    } else {
                        remaining.add(uri)
                    }
                } catch (t: Throwable) {
                    android.util.Log.w(TAG, "SAF deletion failed for $uri", t)
                    remaining.add(uri)
                }
            } else {
                remaining.add(uri)
            }
        }

        // Update internal caches to reflect deletions
        val currentUris = remaining.toSet()
        val remainingFiles = _unnecessaryFiles.value.filter { file ->
            if (file.uri != null) currentUris.contains(file.uri)
            else File(file.path).exists()
        }

        _unnecessaryFiles.value = remainingFiles
        _totalSize.value = remainingFiles.sumOf { it.size }
        _filesByCategory.value = remainingFiles.groupBy { it.type }

        return@withContext remaining
    }

    fun getApkFiles(): List<UnnecessaryFile> = getFilesByCategory(FileType.OBSOLETE_APK)

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

    private fun getPathFromUri(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            var path: String? = null
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idx = getColumnIndexSafe(cursor, MediaStore.MediaColumns.DATA)
                if (idx >= 0 && cursor.moveToFirst()) {
                    path = cursor.getString(idx)
                }
            }
            path
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "getPathFromUri failed for $uri: ${t.message}", t)
            null
        }
    }

    private fun purgeMediaStoreEntryForPath(context: Context, path: String): Int {
        return try {
            val contentUri = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(path)
            val rows = try {
                context.contentResolver.delete(contentUri, selection, selectionArgs)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "MediaStore delete failed for $path: ${t.message}", t)
                0
            }
            if (rows > 0) android.util.Log.d(TAG, "Purged $rows MediaStore entries for $path")
            rows
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "purgeMediaStoreEntryForPath failed for $path: ${t.message}", t)
            0
        }
    }

    fun getFilesByCategory(category: FileType): List<UnnecessaryFile> {
        return _filesByCategory.value[category] ?: emptyList()
    }

    fun getTotalSizeByCategory(category: FileType): Long {
        return getFilesByCategory(category).sumOf { it.size }
    }

    fun getTotalSizeByCategories(categories: Set<FileType>): Long {
        return categories.sumOf { getTotalSizeByCategory(it) }
    }
}