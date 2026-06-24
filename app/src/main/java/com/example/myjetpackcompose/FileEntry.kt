package com.example.myjetpackcompose

import java.io.File

data class FileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val isImage: Boolean,
    val isVideo: Boolean,
    val thumbnailFile: File? = null

)
fun File.toFileEntry(thumbRoot: File? = null): FileEntry {
    val ext = extension.lowercase()
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif")
    val isVideo = ext in listOf("mp4", "mov", "mkv", "webm")

    val thumb = if (isVideo && thumbRoot != null) {
        File(thumbRoot, "$name.jpg").takeIf { it.exists() }
    } else null

    return FileEntry(
        file = this,
        name = name,
        isDirectory = isDirectory,
        isImage = isImage,
        isVideo = isVideo,
        thumbnailFile = thumb
    )
}
