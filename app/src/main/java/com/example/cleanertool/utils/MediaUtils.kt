package com.example.cleanertool.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.cleanertool.data.ImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

object MediaUtils {
    suspend fun scanImages(context: Context): List<ImageData> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageData>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val dateModified = cursor.getLong(dateColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(ImageData(
                    uri = contentUri,
                    name = name,
                    size = size,
                    dateModified = dateModified,
                    isCompressed = false,
                    originalSize = size
                ))
            }
        }

        images
    }
    
    suspend fun getFileFromUri(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            // Always use ContentResolver to read file as stream (works on all Android versions)
            // This avoids permission issues with direct file path access on Android 10+
            val inputStream = try {
                context.contentResolver.openInputStream(uri)
            } catch (t: Throwable) {
                // Some devices/ROMs may crash inside the media provider when attempting
                // to open certain HEIF/HEIC files (see runtime failure in media provider).
                // Swallow provider-side RuntimeExceptions and return null so the app doesn't crash.
                android.util.Log.e("MediaUtils", "Failed to open input stream for $uri: ${t.message}", t)
                null
            } ?: return@withContext null

            inputStream.use { stream ->
                val tempFile = File(context.cacheDir, "temp_compress_${System.currentTimeMillis()}.jpg")
                try {
                    tempFile.outputStream().use { output ->
                        stream.copyTo(output)
                        output.flush()
                    }
                    // Verify file was created successfully
                    if (tempFile.exists() && tempFile.length() > 0) {
                        return@withContext tempFile
                    } else {
                        tempFile.delete()
                        return@withContext null
                    }
                } catch (e: Exception) {
                    // Clean up on error
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    android.util.Log.e("MediaUtils", "Error copying stream to temp file: ${e.message}", e)
                    return@withContext null
                }
            }
        } catch (t: Throwable) {
            // Catch Throwable here as well to ensure Binder or provider-side errors do not crash the process
            android.util.Log.e("MediaUtils", "Unexpected failure while getting file from URI: ${t.message}", t)
            return@withContext null
        }
    }
    
    suspend fun writeCompressedImageToMediaStore(
        context: Context,
        originalUri: Uri,
        compressedFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API to update existing image
                val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
                context.contentResolver.query(originalUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        
                        // Get the MediaStore URI for this image
                        val imageUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        
                        // Try to open output stream with write mode
                        try {
                            context.contentResolver.openOutputStream(imageUri, "w")?.use { outputStream ->
                                FileInputStream(compressedFile).use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                    outputStream.flush()
                                }
                            }
                            return@withContext true
                        } catch (e: Exception) {
                            // If write mode fails, try without mode (append mode)
                            try {
                                context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                                    // Delete existing content first by writing empty
                                    outputStream.write(byteArrayOf())
                                    outputStream.flush()
                                }
                                // Now write the compressed content
                                context.contentResolver.openOutputStream(imageUri, "w")?.use { outputStream ->
                                    FileInputStream(compressedFile).use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                        outputStream.flush()
                                    }
                                }
                                return@withContext true
                            } catch (e2: Exception) {
                                android.util.Log.e("MediaUtils", "Failed to write to existing image: ${e2.message}")
                            }
                        }
                    }
                }
            } else {
                // Android 9 and below - Direct file access
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                context.contentResolver.query(originalUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                        if (filePath != null) {
                            val originalFile = File(filePath)
                            if (originalFile.exists() && originalFile.canWrite()) {
                                // Replace original with compressed file
                                originalFile.delete()
                                compressedFile.copyTo(originalFile, overwrite = true)
                                return@withContext true
                            }
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("MediaUtils", "Failed to write compressed image: ${e.message}", e)
            false
        }
    }
    
    suspend fun saveCompressedImageToAppDirectory(
        context: Context,
        originalName: String,
        compressedFile: File
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Save to app's own Pictures directory
                val contentValues = ContentValues().apply {
                    // Generate a unique name for compressed image
                    val compressedName = if (originalName.contains(".")) {
                        val nameWithoutExt = originalName.substringBeforeLast(".")
                        val ext = originalName.substringAfterLast(".", "jpg")
                        "${nameWithoutExt}_compressed.$ext"
                    } else {
                        "${originalName}_compressed.jpg"
                    }
                    put(MediaStore.Images.Media.DISPLAY_NAME, compressedName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CleanerToolbox")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        FileInputStream(compressedFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                            outputStream.flush()
                        }
                    }
                    
                    // Mark as not pending
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, contentValues, null, null)
                    
                    return@withContext it
                }
            } else {
                // Android 9 and below - Save to app's Pictures directory
                val picturesDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "CleanerToolbox"
                )
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs()
                }
                
                val compressedName = if (originalName.contains(".")) {
                    val nameWithoutExt = originalName.substringBeforeLast(".")
                    val ext = originalName.substringAfterLast(".", "jpg")
                    "${nameWithoutExt}_compressed.$ext"
                } else {
                    "${originalName}_compressed.jpg"
                }
                
                val outputFile = File(picturesDir, compressedName)
                compressedFile.copyTo(outputFile, overwrite = true)
                
                // Add to MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, outputFile.absolutePath)
                    put(MediaStore.Images.Media.DISPLAY_NAME, compressedName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                return@withContext uri
            }
            
            null
        } catch (e: Exception) {
            android.util.Log.e("MediaUtils", "Failed to save compressed image: ${e.message}", e)
            null
        }
    }
    
    suspend fun updateImageInMediaStore(
        context: Context,
        imageUri: Uri,
        compressedFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        // Don't try to overwrite - save to app directory instead
        false
    }
    
    suspend fun compressImageFile(
        context: Context,
        inputFile: File,
        outputFile: File,
        quality: Int = 40,
        maxWidth: Int = 1920,
        maxHeight: Int = 1920
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure output directory exists
            outputFile.parentFile?.mkdirs()
            
            // Load bitmap from input file
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)
            
            // Calculate sample size to reduce memory usage
            var sampleSize = 1
            if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / sampleSize) >= maxHeight && (halfWidth / sampleSize) >= maxWidth) {
                    sampleSize *= 2
                }
            }
            
            // Decode bitmap with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)
                ?: return@withContext false
            
            // Resize if still too large
            val resizedBitmap = if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                val scale = minOf(
                    maxWidth.toFloat() / bitmap.width,
                    maxHeight.toFloat() / bitmap.height
                )
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
            
            // Compress and save
            FileOutputStream(outputFile).use { out ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            
            // Recycle bitmaps
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            bitmap.recycle()
            
            true
        } catch (e: Exception) {
            android.util.Log.e("MediaUtils", "Failed to compress image: ${e.message}", e)
            false
        }
    }
}

