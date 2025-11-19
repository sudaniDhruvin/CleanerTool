package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanertool.data.ImageData
import com.example.cleanertool.utils.MediaUtils
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StorageViewModel(application: Application) : AndroidViewModel(application) {
    private val _images = MutableStateFlow<List<ImageData>>(emptyList())
    val images: StateFlow<List<ImageData>> = _images.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _compressionProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val compressionProgress: StateFlow<Pair<Int, Int>?> = _compressionProgress.asStateFlow()

    fun scanImages(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val scannedImages = MediaUtils.scanImages(context)
                _images.value = scannedImages
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
                            // Get file from URI
                            val inputFile = MediaUtils.getFileFromUri(context, imageData.uri)
                            if (inputFile != null && inputFile.exists()) {
                                val originalSize = inputFile.length()
                                
                                // Compress with 60% size reduction (quality = 40)
                                val compressedFile: File = Compressor.compress(context, inputFile) {
                                    quality(40) // 40% quality = ~60% size reduction
                                    resolution(1920, 1920)
                                    format(android.graphics.Bitmap.CompressFormat.JPEG)
                                }
                                
                                // Replace original with compressed file if compression was successful
                                if (compressedFile.exists() && compressedFile.length() < originalSize) {
                                    // If inputFile is the original file (not temp), replace it
                                    if (!inputFile.absolutePath.contains(context.cacheDir.absolutePath)) {
                                        // Original file - replace it
                                        inputFile.delete()
                                        compressedFile.copyTo(inputFile, overwrite = true)
                                        compressedFile.delete()
                                        successCount++
                                    } else {
                                        // Temp file - need to write back to MediaStore
                                        // For now, just keep the compressed file
                                        // In a production app, you'd write back to MediaStore
                                        successCount++
                                    }
                                } else {
                                    // Compression didn't reduce size, clean up
                                    if (inputFile.absolutePath.contains(context.cacheDir.absolutePath)) {
                                        inputFile.delete()
                                    }
                                    if (compressedFile.exists()) {
                                        compressedFile.delete()
                                    }
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
                    _images.value = scannedImages
                    
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

    fun compressSingleImage(context: Context, imageData: ImageData) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                withContext(Dispatchers.IO) {
                    // Get file from URI
                    val inputFile = MediaUtils.getFileFromUri(context, imageData.uri)
                    if (inputFile != null && inputFile.exists()) {
                        val originalSize = inputFile.length()
                        
                        // Compress with 60% size reduction (quality = 40)
                        val compressedFile: File = Compressor.compress(context, inputFile) {
                            quality(40) // 40% quality = ~60% size reduction
                            resolution(1920, 1920)
                            format(android.graphics.Bitmap.CompressFormat.JPEG)
                        }
                        
                        // Replace original with compressed file if compression was successful
                        if (compressedFile.exists() && compressedFile.length() < originalSize) {
                            // If inputFile is the original file (not temp), replace it
                            if (!inputFile.absolutePath.contains(context.cacheDir.absolutePath)) {
                                // Original file - replace it
                                inputFile.delete()
                                compressedFile.copyTo(inputFile, overwrite = true)
                                compressedFile.delete()
                            }
                        } else {
                            // Compression didn't reduce size, clean up
                            if (inputFile.absolutePath.contains(context.cacheDir.absolutePath)) {
                                inputFile.delete()
                            }
                            if (compressedFile.exists()) {
                                compressedFile.delete()
                            }
                        }
                        
                        // Refresh images list after compression
                        val scannedImages = MediaUtils.scanImages(context)
                        _images.value = scannedImages
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to compress image"
            } finally {
                _isLoading.value = false
            }
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
}

