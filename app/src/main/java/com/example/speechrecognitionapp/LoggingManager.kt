// LoggingManager.kt
package com.example.speechrecognitionapp.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

object LoggingManager {
    private const val LOG_FILE_NAME = "prediction_logs.json"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    /**
     * Save a single log entry to file (append mode).
     */
    fun appendLog(context: Context, entry: LogEntry) {
        try {
            // Load existing logs
            val file = File(context.filesDir, LOG_FILE_NAME)
            val logsArray: JSONArray = if (file.exists()) {
                val existing = file.readText()
                if (existing.isNotEmpty()) JSONArray(existing) else JSONArray()
            } else {
                JSONArray()
            }

            // Create JSON object for the new entry
            val logJson = JSONObject()
            logJson.put("Word", entry.topKeyword)
            logJson.put("confidence", entry.confidence)

            // Add to array
            logsArray.put(logJson)

            // Overwrite file with updated array
            val writer = FileWriter(file, false)
            writer.write(logsArray.toString())
            writer.close()
            Log.d("LoggingManager", "Attempting to write logs to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("LoggingManager", "Error appending logs: ${e.localizedMessage}")
        }
    }

    /**
     * Fetch logs from file and optionally clear them afterward.
     */
    fun fetchAndClearLogs(context: Context): String {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return ""

        val logs = file.readText()
        // Clear file once we have the logs
        file.writeText("")
        return logs
    }
}
