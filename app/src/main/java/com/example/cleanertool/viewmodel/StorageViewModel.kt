package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanertool.data.ImageData
import com.example.cleanertool.utils.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StorageViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = application.getSharedPreferences("compressed_images", Context.MODE_PRIVATE)
    private val COMPRESSED_URIS_KEY = "compressed_uris"
    
    private val _images = MutableStateFlow<List<ImageData>>(emptyList())
    val images: StateFlow<List<ImageData>> = _images.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _compressionProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val compressionProgress: StateFlow<Pair<Int, Int>?> = _compressionProgress.asStateFlow()

    private val _compressedImages = MutableStateFlow<Set<Uri>>(loadCompressedImages())
    val compressedImages: StateFlow<Set<Uri>> = _compressedImages.asStateFlow()
    
    // Store compressed sizes: Map<Uri, Pair<originalSize, compressedSize>>
    private val compressedSizes = mutableMapOf<Uri, Pair<Long, Long>>()
    
    init {
        // Load persisted compressed images on initialization
        _compressedImages.value = loadCompressedImages()
    }
    
    private fun loadCompressedImages(): Set<Uri> {
        val uriStrings = prefs.getStringSet(COMPRESSED_URIS_KEY, emptySet()) ?: emptySet()
        return uriStrings.mapNotNull { Uri.parse(it) }.toSet()
    }
    
    private fun saveCompressedImages(uris: Set<Uri>) {
        val uriStrings = uris.map { it.toString() }.toSet()
        prefs.edit().putStringSet(COMPRESSED_URIS_KEY, uriStrings).apply()
    }
    
    private fun saveCompressedSize(uri: Uri, originalSize: Long, compressedSize: Long) {
        compressedSizes[uri] = Pair(originalSize, compressedSize)
        // Also persist to SharedPreferences
        val key = "size_${uri.toString().hashCode()}"
        prefs.edit().putLong("${key}_original", originalSize)
            .putLong("${key}_compressed", compressedSize)
            .apply()
    }
    
    private fun loadCompressedSize(uri: Uri): Pair<Long, Long>? {
        // First check memory
        compressedSizes[uri]?.let { return it }
        // Then check SharedPreferences
        val key = "size_${uri.toString().hashCode()}"
        val original = prefs.getLong("${key}_original", -1L)
        val compressed = prefs.getLong("${key}_compressed", -1L)
        return if (original > 0 && compressed > 0) {
            Pair(original, compressed).also { compressedSizes[uri] = it }
        } else null
    }
    
    private val _currentCompressingImage = MutableStateFlow<Uri?>(null)
    val currentCompressingImage: StateFlow<Uri?> = _currentCompressingImage.asStateFlow()
    
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()
    
    private val _selectedImageData = MutableStateFlow<ImageData?>(null)
    val selectedImageData: StateFlow<ImageData?> = _selectedImageData.asStateFlow()
    
    fun setSelectedImage(imageData: ImageData) {
        _selectedImageData.value = imageData
        _selectedImageUri.value = imageData.uri
    }
    
    fun setSelectedImage(uri: Uri) {
        _selectedImageUri.value = uri
        // Try to find image data from current images list
        _selectedImageData.value = _images.value.find { it.uri.toString() == uri.toString() }
    }

    fun scanImages(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val scannedImages = MediaUtils.scanImages(context)
                
                // Filter out compressed images (those with "_compressed" in name or in CleanerToolbox directory)
                val filteredImages = scannedImages.filter { img ->
                    !img.name.contains("_compressed", ignoreCase = true) &&
                    !img.name.contains("CleanerToolbox", ignoreCase = true)
                }
                
                // Preserve compression status and sizes for already compressed images
                _images.value = filteredImages.map { img ->
                    val wasCompressed = _compressedImages.value.contains(img.uri)
                    if (wasCompressed) {
                        // Load compressed size info
                        val sizeInfo = loadCompressedSize(img.uri)
                        val originalSize = sizeInfo?.first ?: img.size
                        val compressedSize = sizeInfo?.second ?: img.size
                        
                        img.copy(
                            isCompressed = true,
                            originalSize = originalSize,
                            // Use compressed size for display
                            size = compressedSize
                        )
                    } else {
                        // New image - originalSize equals current size
                        img.copy(
                            originalSize = img.size
                        )
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to scan images"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun compressImages(context: Context, images: List<ImageData>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _compressionProgress.value = Pair(0, images.size)
            
            try {
                withContext(Dispatchers.IO) {
                    var successCount = 0
                    
                    images.forEachIndexed { index, imageData ->
                        try {
                            // Get file from URI and ensure it's in cache directory
                            val inputFile = MediaUtils.getFileFromUri(context, imageData.uri)
                            if (inputFile != null && inputFile.exists()) {
                                val originalSize = inputFile.length()
                                
                                // Ensure input file is in cache directory to avoid permission issues
                                val cacheInputFile = if (inputFile.absolutePath.contains(context.cacheDir.absolutePath)) {
                                    inputFile
                                } else {
                                    // Copy to cache directory first
                                    val cacheFile = File(context.cacheDir, "compress_input_${System.currentTimeMillis()}_${index}.jpg")
                                    inputFile.copyTo(cacheFile, overwrite = true)
                                    cacheFile
                                }
                                
                                // Compress with 60% size reduction (quality = 40)
                                // Use custom compression function to control output location
                                val compressedFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}_${index}.jpg")
                                val compressionSuccess = MediaUtils.compressImageFile(
                                    context,
                                    cacheInputFile,
                                    compressedFile,
                                    quality = 40,
                                    maxWidth = 1920,
                                    maxHeight = 1920
                                )
                                
                                val safeCompressedFile = if (compressionSuccess && compressedFile.exists()) {
                                    compressedFile
                                } else {
                                    null
                                }
                                
                                // Save compressed image to app's directory if compression was successful
                                if (safeCompressedFile != null && safeCompressedFile.exists() && safeCompressedFile.length() < originalSize) {
                                    val finalSize = safeCompressedFile.length()
                                    
                                    // Save compressed image to app's Pictures directory using MediaStore
                                    val compressedUri = MediaUtils.saveCompressedImageToAppDirectory(
                                        context,
                                        imageData.name,
                                        safeCompressedFile
                                    )
                                    
                                    if (compressedUri != null) {
                                        // Save compressed size info
                                        saveCompressedSize(imageData.uri, originalSize, finalSize)
                                        // Mark original as compressed and persist
                                        val updatedSet = _compressedImages.value + imageData.uri
                                        _compressedImages.value = updatedSet
                                        saveCompressedImages(updatedSet)
                                        successCount++
                                    }
                                    
                                    // Clean up temp compressed file
                                    safeCompressedFile.delete()
                                } else {
                                    // Compression didn't reduce size or file not accessible, clean up
                                    if (safeCompressedFile != null && safeCompressedFile.exists()) {
                                        safeCompressedFile.delete()
                                    }
                                }
                                
                                // Clean up cache input file if we created it
                                if (cacheInputFile != inputFile && cacheInputFile.exists()) {
                                    cacheInputFile.delete()
                                }
                            }
                        } catch (e: Exception) {
                            // Continue with next image if one fails
                            android.util.Log.e("StorageViewModel", "Failed to compress ${imageData.name}: ${e.message}")
                        }
                        
                        _compressionProgress.value = Pair(index + 1, images.size)
                    }
                    
                    // Refresh images list after compression
                    val scannedImages = MediaUtils.scanImages(context)
                    _images.value = scannedImages.map { img ->
                        val wasCompressed = _compressedImages.value.contains(img.uri)
                        img.copy(
                            isCompressed = wasCompressed,
                            originalSize = if (wasCompressed) img.size else img.size
                        )
                    }
                    
                    if (successCount > 0) {
                        _error.value = null
                    } else {
                        _error.value = "No images were compressed"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to compress images"
            } finally {
                _isLoading.value = false
                _compressionProgress.value = null
            }
        }
    }

    fun compressSingleImage(context: Context, imageData: ImageData, onComplete: (Long?) -> Unit = {}, onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _currentCompressingImage.value = imageData.uri
            
            var inputFile: File? = null
            var cacheInputFile: File? = null
            var compressedFile: File? = null
            
            try {
                val compressedSize = withContext(Dispatchers.IO) {
                    try {
                        // Get file from URI - this now always uses ContentResolver
                        inputFile = MediaUtils.getFileFromUri(context, imageData.uri)
                            ?: throw Exception("Unable to access image file. Please check permissions.")
                        
                        if (!inputFile!!.exists() || inputFile!!.length() == 0L) {
                            throw Exception("Image file is empty or does not exist.")
                        }
                        
                        val originalSize = inputFile!!.length()
                        
                        // Input file is already in cache directory (getFileFromUri always creates temp file)
                        cacheInputFile = inputFile
                        
                        // Compress with 60% size reduction (quality = 40)
                        compressedFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
                        val compressionSuccess = MediaUtils.compressImageFile(
                            context,
                            cacheInputFile!!,
                            compressedFile!!,
                            quality = 40,
                            maxWidth = 1920,
                            maxHeight = 1920
                        )
                        
                        if (!compressionSuccess || !compressedFile!!.exists() || compressedFile!!.length() == 0L) {
                            throw Exception("Failed to compress image. The image may be corrupted.")
                        }
                        
                        val compressedSize = compressedFile!!.length()
                        
                        // Only proceed if compression actually reduced size
                        if (compressedSize >= originalSize) {
                            throw Exception("Compression did not reduce file size. Image may already be optimized.")
                        }
                        
                        // Save compressed image to app's Pictures directory using MediaStore
                        val compressedUri = MediaUtils.saveCompressedImageToAppDirectory(
                            context,
                            imageData.name,
                            compressedFile!!
                        )
                        
                        if (compressedUri == null) {
                            throw Exception("Failed to save compressed image. Please check storage permissions.")
                        }
                        
                        // Save compressed size info
                        saveCompressedSize(imageData.uri, originalSize, compressedSize)
                        
                        // Mark original as compressed (we'll track by URI) and persist
                        val updatedSet = _compressedImages.value + imageData.uri
                        _compressedImages.value = updatedSet
                        saveCompressedImages(updatedSet)
                        
                        // Refresh images list after compression
                        val scannedImages = MediaUtils.scanImages(context)
                        val filteredImages = scannedImages.filter { img ->
                            !img.name.contains("_compressed", ignoreCase = true) &&
                            !img.name.contains("CleanerToolbox", ignoreCase = true)
                        }
                        
                        _images.value = filteredImages.map { img ->
                            if (img.uri == imageData.uri) {
                                // Update original image to show it's compressed
                                img.copy(
                                    size = compressedSize, // Compressed size
                                    isCompressed = true,
                                    originalSize = originalSize // Preserve original size
                                )
                            } else {
                                val wasCompressed = _compressedImages.value.contains(img.uri)
                                if (wasCompressed) {
                                    // Load compressed size info
                                    val sizeInfo = loadCompressedSize(img.uri)
                                    val origSize = sizeInfo?.first ?: img.size
                                    val compSize = sizeInfo?.second ?: img.size
                                    img.copy(
                                        isCompressed = true,
                                        originalSize = origSize,
                                        size = compSize
                                    )
                                } else {
                                    // Non-compressed image
                                    img.copy(
                                        isCompressed = false,
                                        originalSize = img.size
                                    )
                                }
                            }
                        }
                        
                        compressedSize
                    } catch (e: Exception) {
                        // Build detailed error message
                        val errorMsg = when {
                            e.message?.contains("EACCES") == true || e.message?.contains("Permission denied") == true -> {
                                val fileName = imageData.name
                                "File access denied:\n$fileName\n\nPlease grant storage permissions in app settings."
                            }
                            e.message?.contains("open failed") == true -> {
                                val fileName = imageData.name
                                "$fileName:\nopen failed: EACCES (Permission denied)\n\nPlease check app permissions."
                            }
                            else -> {
                                e.message ?: "Failed to compress image. Please try again."
                            }
                        }
                        
                        // Clean up files on error
                        try {
                            inputFile?.delete()
                            cacheInputFile?.delete()
                            compressedFile?.delete()
                        } catch (cleanupError: Exception) {
                            android.util.Log.e("StorageViewModel", "Error cleaning up files: ${cleanupError.message}")
                        }
                        
                        // Call error callback if provided
                        onError?.invoke(errorMsg)
                        
                        throw Exception(errorMsg)
                    }
                }
                
                onComplete(compressedSize)
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Failed to compress image. Please try again."
                _error.value = errorMsg
                onError?.invoke(errorMsg)
                onComplete(null)
            } finally {
                // Final cleanup
                try {
                    inputFile?.delete()
                    cacheInputFile?.delete()
                    compressedFile?.delete()
                } catch (cleanupError: Exception) {
                    android.util.Log.e("StorageViewModel", "Error in final cleanup: ${cleanupError.message}")
                }
                
                _isLoading.value = false
                _currentCompressingImage.value = null
            }
        }
    }
    
    fun estimateCompressedSize(originalSize: Long): Long {
        // Estimate ~60% reduction (40% of original)
        return (originalSize * 0.4).toLong()
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

