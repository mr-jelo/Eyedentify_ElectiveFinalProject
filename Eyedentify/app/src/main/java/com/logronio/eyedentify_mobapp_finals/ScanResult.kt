package com.logronio.eyedentify_mobapp_finals

import java.util.*

data class ScanResult(
    val id: String = "", // Firebase document ID
    val imageUri: String,
    val detectedText: String,
    val detectedObjects: List<String> = emptyList(),
    val timestamp: Date = Date()
)