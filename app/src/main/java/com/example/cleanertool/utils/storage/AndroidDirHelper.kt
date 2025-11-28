package com.example.cleanertool.utils.storage

import java.io.File

/**
 * Utilities copied conceptually from Smart Cleaner to keep scanning behaviour in sync.
 * They guard protected directories like /Android/data and /Android/obb that require
 * MANAGE_EXTERNAL_STORAGE to access.
 */
fun File.isProtectedAndroidDir(): Boolean {
    val parts = absolutePath.split(File.separatorChar).filter { it.isNotEmpty() }
    parts.forEachIndexed { index, segment ->
        if (segment.equals("Android", ignoreCase = true)) {
            when (parts.getOrNull(index + 1)?.lowercase()) {
                "data", "obb" -> return true
            }
        }
    }
    return false
}

fun File.shouldSkip(showHidden: Boolean): Boolean {
    return (!showHidden && isHidden) || isProtectedAndroidDir()
}

