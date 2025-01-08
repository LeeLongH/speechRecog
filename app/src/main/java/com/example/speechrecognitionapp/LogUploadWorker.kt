package com.example.speechrecognitionapp.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.speechrecognitionapp.logging.LoggingManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class LogUploadWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        // 1) Fetch logs from local storage (JSON string)
        val logs = LoggingManager.fetchAndClearLogs(applicationContext)
        if (logs.isBlank()) {
            // Nothing to upload
            return Result.success()
        }

        // 2) Upload logs to Firebase Cloud Storage
        return try {
            // Reference to Firebase Storage root
            val storage = FirebaseStorage.getInstance()
            // We'll store logs under "logs/" folder, with a unique name
            val fileName = "logs/${UUID.randomUUID()}.json"
            val storageRef = storage.reference.child(fileName)

            // Convert logs JSON string to bytes
            val logsData = logs.toByteArray(Charsets.UTF_8)
            val uploadTask = storageRef.putBytes(logsData)

            // Wait for the upload to finish
            Tasks.await(uploadTask)

            // If we reach here, upload succeeded
            Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            // Return failure to let WorkManager retry if needed
            Result.failure()
        }
    }
}
