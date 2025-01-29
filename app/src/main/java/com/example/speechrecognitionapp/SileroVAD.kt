import ai.onnxruntime.*
import android.content.Context
import java.nio.FloatBuffer

class SileroVAD(context: Context) {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val ortSession: OrtSession

    // Initialize hidden state `h` with the correct shape [2, 1, 64]
    private var hiddenState: OnnxTensor? = null
    private var contextTensor: OnnxTensor? = null

    init {
        val modelPath = "silero_vad.onnx"
        val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        ortSession = ortEnv.createSession(modelBytes, options)

        //Ensure `h` has correct shape [2, 1, 64]
        hiddenState = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.allocate(2 * 1 * 64),
            longArrayOf(2, 1, 64)
        )

        // Initialize `c` tensor with zeros (shape matches `h`)
        contextTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.allocate(2 * 1 * 64),
            longArrayOf(2, 1, 64)
        )
    }

    fun isSpeechDetected(audioBuffer: FloatArray): Boolean {
        val shape = longArrayOf(1, audioBuffer.size.toLong()) // Fix: Ensure `[1, N]` shape

        // Create input tensor for audio
        val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audioBuffer), shape)

        // Add `sr` (Sample Rate) input
        val srTensor = OnnxTensor.createTensor(ortEnv, longArrayOf(16000))

        // Ensure all required inputs are included
        val inputs = mapOf(
            "input" to inputTensor,
            "sr" to srTensor,
            "h" to hiddenState!!,
            "c" to contextTensor!!
        )

        val output = ortSession.run(inputs)

        val result = (output[0].value as Array<FloatArray>)[0][0] // Extract single float value

        val speechDetected = result > 0.5f // Speech detected if result > 0.5

        hiddenState?.close() // Release old state
        contextTensor?.close()
        hiddenState = output[1] as OnnxTensor
        contextTensor = output[2] as OnnxTensor

        return speechDetected
    }
}
