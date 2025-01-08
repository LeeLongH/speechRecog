// LogEntry.kt
package com.example.speechrecognitionapp.logging

data class LogEntry(
    val timestamp: Long,
    val topKeyword: String,
    val confidence: Double
)
