package com.example.myjetpackcompose

data class ImportState(
    val isImporting: Boolean = false,
    val currentFileName: String = "",
    val currentIndex: Int = 0,
    val totalFiles: Int = 0,
    val progress: Float = 0f,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val errorMessage: String? = null
)
