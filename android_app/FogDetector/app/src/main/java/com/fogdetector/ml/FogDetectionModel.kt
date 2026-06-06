package com.fogdetector.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Wraps the dual-head TFLite model.
 *
 * Model file  : assets/fog_model.tflite
 *
 * Input       : [1, 120, 6]  float32  → 2880 bytes
 *
 * Output 0    : [1, 1]  float32  → FOG sigmoid
 *                 name: StatefulPartitionedCall_1:1
 *
 * Output 1    : [1, 4]  float32  → Activity softmax
 *                 name: StatefulPartitionedCall_1:0
 *                 classes: [Other, Stationary, Walking, Shuffling]
 */
class FogDetectionModel(private val context: Context) {

    companion object {
        private const val TAG        = "FogDetectionModel"
        private const val MODEL_FILE = "fog_model.tflite"

        // ── Input ──────────────────────────────────────────────
        private const val INPUT_SAMPLES = 120 * 6
        private const val FLOAT_BYTES   = 4
        // [1, 120, 6] float32 = 2880 bytes
        private const val INPUT_BYTES   = INPUT_SAMPLES * FLOAT_BYTES
        private const val RAW_PACKET_BYTES = INPUT_SAMPLES

        // ── Output 0: FOG ──────────────────────────────────────
        // [1, 1] float32 = 4 bytes
        private const val IDX_FOG      = 0
        private const val FOG_BYTES    = FLOAT_BYTES

        // ── Output 1: Activity ─────────────────────────────────
        // [1, 4] float32 = 16 bytes
        private const val IDX_ACTIVITY = 1
        private const val ACT_CLASSES  = 4
        private const val ACT_BYTES    = ACT_CLASSES * FLOAT_BYTES

        // ── Activity class names ───────────────────────────────
        val ACTIVITY_NAMES = arrayOf("Other", "Stationary", "Walking", "Shuffling")
    }

    private var interpreter: Interpreter? = null
    val isLoaded: Boolean get() = interpreter != null

    // ── Lifecycle ─────────────────────────────────────────────

    fun load(): Boolean {
        return try {
            val assetFd = context.assets.openFd(MODEL_FILE)
            val buffer  = FileInputStream(assetFd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength
            )
            interpreter = Interpreter(buffer, Interpreter.Options().apply {
                setNumThreads(4)
            })
            logAndValidateTensors()
            Log.i(TAG, "Model loaded OK")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed: ${e.message}")
            false
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    // ── Inference ─────────────────────────────────────────────

    /**
     * Runs inference on one 120x6 window assembled as 720 signed sensor bytes.
     *
     * The BLE/windowing path stays byte-based; this method converts each signed
     * sample to float32 for the model's float input tensor.
     *
     * @param rawPacket   720 signed bytes from IMU, row-major [120][6]
     * @param fogThreshold  Probability above which isFog=true (default 0.5)
     */
    fun predict(rawPacket: ByteArray, fogThreshold: Float = 0.5f): PredictionResult? {
        val interp = interpreter ?: run {
            Log.w(TAG, "Interpreter null")
            return null
        }
        if (rawPacket.size != RAW_PACKET_BYTES) {
            Log.e(TAG, "Wrong packet size: expected $RAW_PACKET_BYTES, got ${rawPacket.size}")
            return null
        }

        // The model expects float32 input, so expand each signed sensor byte to a float.
        val inputBuf = ByteBuffer.allocateDirect(INPUT_BYTES).apply {
            order(ByteOrder.nativeOrder())
            rawPacket.forEach { putFloat(it.toFloat()) }
            rewind()
        }

        val fogOutput = Array(1) { FloatArray(1) }
        val activityOutput = Array(1) { FloatArray(ACT_CLASSES) }

        return try {
            interp.runForMultipleInputsOutputs(
                arrayOf(inputBuf),
                mapOf(
                    IDX_FOG      to fogOutput,
                    IDX_ACTIVITY to activityOutput
                )
            )

            val fogProb = fogOutput[0][0]
            val actProbs = activityOutput[0].copyOf()
            val actClass = actProbs.indices.maxByOrNull { actProbs[it] } ?: 0

            Log.d(TAG,
                "FOG=%.3f[%s] | Act=%s(%.2f) | act=%s".format(
                    fogProb,
                    if (fogProb >= fogThreshold) "FOG" else "OK",
                    ACTIVITY_NAMES[actClass],
                    actProbs[actClass],
                    actProbs.toList().toString()
                )
            )

            PredictionResult(
                fogProbability = fogProb,
                activityProbs  = actProbs,
                activityClass  = actClass,
                isFog          = fogProb >= fogThreshold
            )

        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            null
        }
    }

    /**
     * Log tensor details and validate buffer sizes at load time.
     * Check logcat tag "FogDetectionModel" after app starts.
     */
    private fun logAndValidateTensors() {
        val interp = interpreter ?: return

        Log.d(TAG, "=== INPUT TENSORS ===")
        for (i in 0 until interp.inputTensorCount) {
            val t = interp.getInputTensor(i)
            Log.d(TAG, "  Input[$i]: shape=${t.shape().toList()} " +
                    "dtype=${t.dataType()} bytes=${t.numBytes()}")
        }

        Log.d(TAG, "=== OUTPUT TENSORS ===")
        for (i in 0 until interp.outputTensorCount) {
            val t = interp.getOutputTensor(i)
            Log.d(TAG, "  Output[$i]: name=${t.name()} " +
                    "shape=${t.shape().toList()} " +
                    "dtype=${t.dataType()} bytes=${t.numBytes()}")
        }

        val inputActual = interp.getInputTensor(0).numBytes()
        if (inputActual != INPUT_BYTES)
            Log.e(TAG, "⚠️ Input size mismatch: expected=$INPUT_BYTES actual=$inputActual")
        else
            Log.i(TAG, "✅ Input buffer OK ($INPUT_BYTES bytes)")

        // Validate FOG buffer
        val fogActual = interp.getOutputTensor(IDX_FOG).numBytes()
        if (fogActual != FOG_BYTES)
            Log.e(TAG, "⚠️ FOG size mismatch: expected=$FOG_BYTES actual=$fogActual")
        else
            Log.i(TAG, "✅ FOG buffer OK ($FOG_BYTES bytes)")

        // Validate Activity buffer
        val actActual = interp.getOutputTensor(IDX_ACTIVITY).numBytes()
        if (actActual != ACT_BYTES)
            Log.e(TAG, "⚠️ Activity size mismatch: expected=$ACT_BYTES actual=$actActual")
        else
            Log.i(TAG, "✅ Activity buffer OK ($ACT_BYTES bytes)")
    }
}
