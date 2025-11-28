package com.example.cleanertool.utils.storage

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight replica of Smart Cleaner's directory traversal helper so that the junk scan
 * can iterate over the same set of files.
 */
object DirectoryScanner {

    suspend fun scan(
        root: File,
        showHidden: Boolean = false,
        skipDir: (File) -> Boolean = { false },
        onLockedDir: ((File) -> Unit)? = null,
        onFile: suspend (File) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext

        val iterator = root.walkTopDown()
            .onEnter { dir ->
                if ((!showHidden && dir.isHidden) || skipDir(dir)) {
                    if (dir.isProtectedAndroidDir()) {
                        onLockedDir?.invoke(dir)
                    }
                    return@onEnter false
                }

                if (dir.isProtectedAndroidDir()) {
                    onLockedDir?.invoke(dir)
                    return@onEnter false
                }

                true
            }
            .iterator()

        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file.isFile && (showHidden || !file.isHidden)) {
                onFile(file)
            }
        }
    }
}

