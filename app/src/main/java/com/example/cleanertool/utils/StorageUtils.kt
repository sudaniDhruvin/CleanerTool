package com.example.cleanertool.utils

import android.content.Context
import android.os.StatFs
import android.os.Build
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val usagePercentage: Int
)

object StorageUtils {
    suspend fun getStorageInfo(context: Context): StorageInfo = withContext(Dispatchers.IO) {
        val stat = StatFs(File(context.filesDir.parent).absolutePath)
        
        val totalSpace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.totalBytes
        } else {
            @Suppress("DEPRECATION")
            stat.blockCount.toLong() * stat.blockSize
        }
        
        val freeSpace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.availableBytes
        } else {
            @Suppress("DEPRECATION")
            stat.availableBlocks.toLong() * stat.blockSize
        }
        
        val usedSpace = totalSpace - freeSpace
        val usagePercentage = if (totalSpace > 0) {
            ((usedSpace.toDouble() / totalSpace.toDouble()) * 100).toInt()
        } else {
            0
        }
        
        StorageInfo(
            totalSpace = totalSpace,
            freeSpace = freeSpace,
            usedSpace = usedSpace,
            usagePercentage = usagePercentage
        )
    }
    
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

