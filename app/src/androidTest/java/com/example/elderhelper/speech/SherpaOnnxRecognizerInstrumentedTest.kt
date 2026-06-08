package com.example.elderhelper.speech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaOnnxRecognizerInstrumentedTest {
    @Test
    fun decodesPcmThroughSherpaOnnxSttEngineWhenModelPresent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = File(context.filesDir, "sherpa-onnx/asr-test/2-zh-en.wav")
        assumeLocalModelAndTestWavArePresent(wav)

        val audio = readPcm16MonoWav(wav)
        val engine = SherpaOnnxSttEngine(context)
        try {
            val result = engine.transcribePcm16Le(audio.pcm16Le)
            assertTrue("Expected ElderHelper STT engine to produce text.", result.isSuccess)
        } finally {
            engine.release()
        }
    }

    @Test
    fun decodesBundledDeviceModelWhenPresent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wav = File(context.filesDir, "sherpa-onnx/asr-test/2-zh-en.wav")
        assumeLocalModelAndTestWavArePresent(wav)

        val modelDir = File(context.filesDir, "sherpa-onnx/asr")
        val model = listOf("model.int8.onnx", "model.onnx")
            .map { File(modelDir, it) }
            .firstOrNull { it.exists() }
        val tokens = File(modelDir, "tokens.txt")

        val audio = readPcm16MonoWav(wav)
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = audio.sampleRate, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    paraformer = OfflineParaformerModelConfig(model = model!!.absolutePath),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            ),
        )

        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(audio.samples, audio.sampleRate)
                recognizer.decode(stream)
                val text = recognizer.getResult(stream).text
                assertTrue("Expected non-empty local STT result.", text.isNotBlank())
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }

    private fun assumeLocalModelAndTestWavArePresent(wav: File) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelDir = File(context.filesDir, "sherpa-onnx/asr")
        val model = listOf("model.int8.onnx", "model.onnx")
            .map { File(modelDir, it) }
            .firstOrNull { it.exists() }
        val tokens = File(modelDir, "tokens.txt")
        assumeTrue("Local sherpa-onnx ASR model is not installed on this test device.", model != null)
        assumeTrue("Local sherpa-onnx tokens.txt is not installed on this test device.", tokens.exists())
        assumeTrue("Local sherpa-onnx test wav is not installed on this test device.", wav.exists())
    }

    private fun readPcm16MonoWav(file: File): PcmAudio {
        val bytes = file.readBytes()
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE")

        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            val chunkDataOffset = offset + 8
            when (chunkId) {
                "fmt " -> {
                    val fmt = ByteBuffer.wrap(bytes, chunkDataOffset, chunkSize)
                        .order(ByteOrder.LITTLE_ENDIAN)
                    val audioFormat = fmt.short.toInt()
                    channels = fmt.short.toInt()
                    sampleRate = fmt.int
                    fmt.int
                    fmt.short
                    bitsPerSample = fmt.short.toInt()
                    require(audioFormat == 1) { "Only PCM wav files are supported." }
                }
                "data" -> {
                    dataOffset = chunkDataOffset
                    dataSize = chunkSize
                }
            }
            offset = chunkDataOffset + chunkSize + (chunkSize % 2)
        }

        require(sampleRate > 0 && dataOffset >= 0)
        require(channels == 1) { "Only mono wav files are supported." }
        require(bitsPerSample == 16) { "Only 16-bit wav files are supported." }

        val sampleCount = dataSize / 2
        val pcm16Le = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
        val samples = FloatArray(sampleCount)
        val pcm = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until sampleCount) {
            samples[index] = pcm.short / 32768.0f
        }
        return PcmAudio(sampleRate, samples, pcm16Le)
    }

    private data class PcmAudio(
        val sampleRate: Int,
        val samples: FloatArray,
        val pcm16Le: ByteArray,
    )
}
