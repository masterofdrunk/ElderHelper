package com.example.elderhelper.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.elderhelper.model.ModelAssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SherpaOnnxSttEngine(
    private val context: Context,
) : SpeechToTextEngine {
    private val recording = AtomicBoolean(false)
    private val audioBuffer = ByteArrayOutputStream()
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var recognizer: OfflineRecognizer? = null

    override val isRecording: Boolean
        get() = recording.get()

    override fun startListening() {
        if (recording.get()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("RECORD_AUDIO permission is missing.")
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize,
        )

        audioBuffer.reset()
        audioRecord = recorder
        recording.set(true)
        recorder.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(minBufferSize)
            while (recording.get()) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(audioBuffer) {
                        audioBuffer.write(buffer, 0, read)
                    }
                }
            }
        }.apply {
            name = "SherpaOnnxSttRecorder"
            start()
        }
    }

    override suspend fun stopAndTranscribe(): SttResult = withContext(Dispatchers.IO) {
        stopRecording()
        val audioBytes = synchronized(audioBuffer) { audioBuffer.toByteArray() }
        if (audioBytes.isEmpty()) {
            return@withContext SttResult(errorMessage = "没有听到声音，请靠近手机再说一次。")
        }
        if (!isModelInstalled()) {
            Log.w(TAG, "Local STT model is missing. Expected files under ${modelDir().path}.")
            return@withContext SttResult(errorMessage = "还没有安装离线语音包。请先安装后再使用语音识别。")
        }

        try {
            transcribePcm16Le(audioBytes)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "sherpa-onnx native runtime failed to load.", e)
            SttResult(errorMessage = "离线语音运行库加载失败，请重新安装应用后再试。")
        } catch (e: RuntimeException) {
            Log.e(TAG, "sherpa-onnx recognition failed.", e)
            SttResult(errorMessage = "离线语音识别失败，请检查语音包是否完整。")
        }
    }

    override fun cancel() {
        stopRecording()
        synchronized(audioBuffer) {
            audioBuffer.reset()
        }
    }

    override fun release() {
        cancel()
        recognizer?.release()
        recognizer = null
    }

    private fun stopRecording() {
        if (!recording.getAndSet(false)) return
        try {
            recordingThread?.join(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        recordingThread = null
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord.", e)
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord.", e)
        }
        audioRecord = null
    }

    internal fun transcribePcm16Le(audioBytes: ByteArray): SttResult {
        val samples = pcm16LeToFloatArray(audioBytes)
        if (samples.isEmpty()) {
            return SttResult(errorMessage = "没有听到声音，请靠近手机再说一次。")
        }

        val activeRecognizer = getOrCreateRecognizer()
        val stream = activeRecognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            activeRecognizer.decode(stream)
            val text = activeRecognizer.getResult(stream).text.trim()
            if (text.isBlank()) {
                SttResult(errorMessage = "没有识别到清晰语音，请再说一次。")
            } else {
                SttResult(text = text)
            }
        } finally {
            stream.release()
        }
    }

    private fun isModelInstalled(): Boolean {
        val dir = modelDir()
        return modelFile(dir) != null && File(dir, "tokens.txt").exists()
    }

    private fun getOrCreateRecognizer(): OfflineRecognizer {
        recognizer?.let { return it }
        val dir = modelDir()
        val model = requireNotNull(modelFile(dir)) { "Missing model.onnx or model.int8.onnx." }
        val tokens = File(dir, "tokens.txt")
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(model = model.absolutePath),
                tokens = tokens.absolutePath,
                numThreads = RECOGNIZER_THREADS,
                debug = false,
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        )
        return OfflineRecognizer(config = config).also {
            recognizer = it
        }
    }

    private fun pcm16LeToFloatArray(bytes: ByteArray): FloatArray {
        val sampleCount = bytes.size / BYTES_PER_SAMPLE
        val samples = FloatArray(sampleCount)
        var byteIndex = 0
        for (sampleIndex in 0 until sampleCount) {
            val low = bytes[byteIndex].toInt() and 0xff
            val high = bytes[byteIndex + 1].toInt()
            val pcm = (high shl 8) or low
            samples[sampleIndex] = pcm / PCM_16BIT_SCALE
            byteIndex += BYTES_PER_SAMPLE
        }
        return samples
    }

    private fun modelFile(dir: File): File? {
        return listOf("model.int8.onnx", "model.onnx")
            .map { File(dir, it) }
            .firstOrNull { it.exists() }
    }

    private fun modelDir(): File = ModelAssetManager(context).sherpaAsrModelDir()

    private companion object {
        private const val TAG = "SherpaOnnxSttEngine"
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80
        private const val RECOGNIZER_THREADS = 2
        private const val BYTES_PER_SAMPLE = 2
        private const val PCM_16BIT_SCALE = 32768.0f
    }
}
