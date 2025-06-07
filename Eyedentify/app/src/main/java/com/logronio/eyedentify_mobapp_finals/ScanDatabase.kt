package com.logronio.eyedentify_mobapp_finals

import java.util.Date

data class ScanDatabase(
    val id: String = "",
    val imageUri: String = "",
    val detectedText: String = "",
    val detectedObjects: List<String> = emptyList(),
    val timestamp: Date? = null
)