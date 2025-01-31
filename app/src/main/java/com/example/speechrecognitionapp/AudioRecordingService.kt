package com.example.speechrecognitionapp

import SileroVAD
import ai.onnxruntime.OnnxTensor
import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.speechrecognitionapp.logging.LogEntry
import com.example.speechrecognitionapp.logging.LoggingManager
import java.util.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.FloatBuffer
import kotlin.collections.ArrayList
import kotlin.math.log10
import kotlin.math.sqrt

class AudioRecordingService : Service() {

    companion object {
        private val TAG = AudioRecordingService::class.simpleName

        private const val SAMPLE_RATE = 16000
        private const val AUDIO_CHANNELS = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_INPUT = MediaRecorder.AudioSource.MIC

        private const val DESIRED_LENGTH_SECONDS = 1
        private const val RECORDING_LENGTH = SAMPLE_RATE * DESIRED_LENGTH_SECONDS // in seconds

        // Model input shape depends on your .tflite
        private const val NUM_MFCC = 13

        // Notifications
        private const val CHANNEL_ID = "word_recognition"
        private const val NOTIFICATION_ID = 202
    }

    // We use this for a simple dB threshold test (in decibels)
    private var dbThreshold = 50

    // These come from SharedPreferences
    private var energyThreshold = 0.1      // Not used here (you can adapt it if you prefer)
    private var probabilityThreshold = 0.002f
    private var windowSize = SAMPLE_RATE / 2
    private var topK = 3

    private var recordingBufferSize = 0
    private var audioRecord: AudioRecord? = null
    private var audioRecordingThread: Thread? = null

    var isRecording: Boolean = false
    var recordingBuffer: DoubleArray = DoubleArray(RECORDING_LENGTH)
    var interpreter: Interpreter? = null

    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notification: Notification? = null

    private var callback: RecordingCallback? = null
    private var isBackground = true

    inner class RunServiceBinder : Binder() {
        val service: AudioRecordingService
            get() = this@AudioRecordingService
    }

    var serviceBinder = RunServiceBinder()

    // Chosen method: "dB" or "Silero"
    private var selectedMethod: String = "dB"

    override fun onCreate() {
        Log.d(TAG, "Creating service")
        super.onCreate()

        createNotificationChannel()

        recordingBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AUDIO_CHANNELS, AUDIO_FORMAT)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Permission denied
            return
        }
        audioRecord = AudioRecord(AUDIO_INPUT, SAMPLE_RATE, AUDIO_CHANNELS, AUDIO_FORMAT, recordingBufferSize)
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Binding service")
        return serviceBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting service")

        if (intent != null) {
            val bundle = intent.extras
            if (bundle != null) {
                energyThreshold = bundle.getDouble("energyThreshold", 0.1)
                probabilityThreshold = bundle.getFloat("probabilityThreshold", 0.002f)
                windowSize = bundle.getInt("windowSize", SAMPLE_RATE / 2)
                topK = bundle.getInt("topK", 3)
                selectedMethod = bundle.getString("method", "dB")
            }
            Log.d(TAG, "Energy threshold: $energyThreshold")
            Log.d(TAG, "Probability threshold: $probabilityThreshold")
            Log.d(TAG, "Window size: $windowSize")
            Log.d(TAG, "Method: $selectedMethod")
        }

        startRecording()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = getString(R.string.channel_desc)
        channel.enableLights(true)
        channel.lightColor = Color.BLUE
        channel.enableVibration(true)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        val resultIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val resultPendingIntent = PendingIntent.getActivity(
            this,
            0,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(resultPendingIntent)

        notificationBuilder = builder
        return builder.build()
    }

    private fun updateNotification(label: String) {
        if (isBackground) return
        if (notificationBuilder == null) return

        notificationBuilder?.setContentText(getText(R.string.notification_prediction).toString() + " " + label)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder?.build())
    }

    fun setCallback(callback: RecordingCallback) {
        this.callback = callback
    }

    private fun updateData(data: ArrayList<Result>) {
        // Sort by confidence desc
        data.sortByDescending { it.confidence }

        // Keep top K
        if (data.size > topK) {
            data.subList(topK, data.size).clear()
        }

        callback?.onDataUpdated(data)
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission denied
            return
        }
        isRecording = true
        audioRecordingThread = Thread { record() }
        audioRecordingThread?.start()
    }

    private fun record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized!")
            return
        }

        audioRecord?.startRecording()
        Log.v(TAG, "Start recording thread")

        var firstLoop = true
        var totalSamplesRead: Int

        while (isRecording) {
            val tempRecordingBuffer = DoubleArray(SAMPLE_RATE - windowSize)

            if (!firstLoop) {
                totalSamplesRead = SAMPLE_RATE - windowSize
            } else {
                totalSamplesRead = 0
                firstLoop = false
            }

            // Fill up one second of audio
            while (totalSamplesRead < SAMPLE_RATE) {
                val remainingSamples = SAMPLE_RATE - totalSamplesRead
                val samplesToRead = if (remainingSamples > recordingBufferSize) recordingBufferSize else remainingSamples
                val audioBuffer = ShortArray(samplesToRead)
                val read = audioRecord?.read(audioBuffer, 0, samplesToRead)

                if (read != AudioRecord.ERROR_INVALID_OPERATION && read != AudioRecord.ERROR_BAD_VALUE && read != null) {
                    for (i in 0 until read) {
                        recordingBuffer[totalSamplesRead + i] = audioBuffer[i].toDouble() / Short.MAX_VALUE
                    }
                    totalSamplesRead += read
                }
            }

            // Choose dB vs Silero logic
            if (selectedMethod == "dB") {
                computeBufferdB(recordingBuffer)
            } else {
                computeBufferSilero(recordingBuffer)
            }

            // Shift the buffer by windowSize
            System.arraycopy(recordingBuffer, windowSize, tempRecordingBuffer, 0, RECORDING_LENGTH - windowSize)
            recordingBuffer = DoubleArray(RECORDING_LENGTH)
            System.arraycopy(tempRecordingBuffer, 0, recordingBuffer, 0, tempRecordingBuffer.size)
        }
        stopRecording()
    }

    /**
     * Simple dB check in decibels, then do MFCC + TFLite if above threshold
     */
    private fun computeBufferdB(audioBuffer: DoubleArray) {
        val decibels = calculateDecibels(audioBuffer)
        Log.d(TAG, "Sound level: $decibels dB")

        if (decibels < dbThreshold) {
            // Show "none" and skip TFLite
            Log.d(TAG, "Sound level below threshold, skipping model inference.")
            callback?.onDataUpdated(arrayListOf(Result("none", 0.0)))
            return
        }

        // Do MFCC, run model
        val mfccConvert = MFCC().apply {
            setSampleRate(SAMPLE_RATE)
            setN_mfcc(NUM_MFCC)
        }
        val mfccInput = mfccConvert.process(audioBuffer)
        loadAndPredict(mfccInput)
    }

    /**
     * Use Silero VAD (user-provided class) to detect speech; skip if none,
     * else do MFCC + TFLite
     */
    private fun computeBufferSilero(audioBuffer: DoubleArray) {
        val vadModel = SileroVAD(this)
        val speechDetected = vadModel.isSpeechDetected(audioBuffer.map { it.toFloat() }.toFloatArray())

        if (!speechDetected) {
            Log.d(TAG, "No speech detected (Silero), skipping model inference.")
            callback?.onDataUpdated(arrayListOf(Result("none", 0.0)))
            return
        }

        // Do MFCC, run model
        val mfccConvert = MFCC().apply {
            setSampleRate(SAMPLE_RATE)
            setN_mfcc(NUM_MFCC)
        }
        val mfccInput = mfccConvert.process(audioBuffer)
        loadAndPredict(mfccInput)
    }

    private fun calculateDecibels(buffer: DoubleArray): Double {
        // Root Mean Square
        val dB = sqrt(buffer.map { it * it }.average())
        // Convert to dB. The +85 is approximate offset for 16-bit scale (adjust as needed).
        return 20 * log10(dB) + 85
    }

    private fun loadAndPredict(mfccs: FloatArray) {
        // Load TFLite model
        val mappedByteBuffer = FileUtil.loadMappedFile(this, "model_16K_LR.tflite")
        interpreter = Interpreter(mappedByteBuffer)

        val inputIndex = 0
        val inputShape = interpreter?.getInputTensor(inputIndex)?.shape()
        val inputType = interpreter?.getInputTensor(inputIndex)?.dataType()

        val outputIndex = 0
        val outputShape = interpreter?.getOutputTensor(outputIndex)?.shape()
        val outputType = interpreter?.getOutputTensor(outputIndex)?.dataType()

        val inputBuffer = TensorBuffer.createFixedSize(inputShape, inputType)
        inputBuffer.loadArray(mfccs, inputShape)

        val outputBuffer = TensorBuffer.createFixedSize(outputShape, outputType)
        interpreter?.run(inputBuffer.buffer, outputBuffer.buffer)

        // Load labels
        val axisLabels = try {
            FileUtil.loadLabels(this, "labels.txt")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading label file", e)
            null
        } ?: return

        // Process the output
        val probabilityProcessor = TensorProcessor.Builder().build()
        val labeledMap = TensorLabel(axisLabels, probabilityProcessor.process(outputBuffer)).mapWithFloatValue

        val results = ArrayList<Result>()
        labeledMap.forEach { (label, confidence) ->
            results.add(Result(label, confidence.toDouble()))
        }

        // Find best prediction
        val bestEntry = labeledMap.maxByOrNull { it.value }
        if (bestEntry != null) {
            val bestLabel = bestEntry.key
            val bestConfidence = bestEntry.value
            Log.d(TAG, "Max Label: $bestLabel, conf=$bestConfidence")

            // If above threshold, show recognized word
            if (bestConfidence > probabilityThreshold) {
                updateData(results)
                // Notification
                notification = createNotification()
                updateNotification(bestLabel)

                // Logging
                val logEntry = LogEntry(topKeyword = bestLabel, confidence = bestConfidence.toDouble())
                LoggingManager.appendLog(this, logEntry)
            } else {
                // Otherwise, force "none"
                callback?.onDataUpdated(arrayListOf(Result("none", 0.0)))
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    fun foreground() {
        notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        isBackground = false
    }

    fun background() {
        isBackground = true
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun stopRecording() {
        isRecording = false
        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                audioRecord?.stop()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }
        }

        audioRecord?.release()
        audioRecord = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
        Log.d(TAG, "Destroying service")
    }
}
