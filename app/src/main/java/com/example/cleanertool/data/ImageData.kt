package com.example.cleanertool.data

import android.net.Uri

data class ImageData(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val isCompressed: Boolean = false,
    val originalSize: Long = size
)

