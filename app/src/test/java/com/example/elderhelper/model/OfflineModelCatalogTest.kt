package com.example.elderhelper.model

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineModelCatalogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun completePackSizeMatchesRequiredArtifacts() {
        assertEquals(
            OfflineModelCatalog.ASR_ARCHIVE_BYTES +
                OfflineModelCatalog.MINICPM_MODEL_BYTES +
                OfflineModelCatalog.MINICPM_PROJECTOR_BYTES,
            OfflineModelCatalog.completePackDownloadBytes,
        )
    }

    @Test
    fun parsesContentRangeTotals() {
        assertEquals(
            529_101_504L,
            ResumableModelDownloader.parseContentRangeTotal("bytes 0-0/529101504"),
        )
        assertEquals(
            1_108_746_944L,
            ResumableModelDownloader.parseContentRangeTotal("bytes */1108746944"),
        )
        assertNull(ResumableModelDownloader.parseContentRangeTotal(null))
        assertNull(ResumableModelDownloader.parseContentRangeTotal("invalid"))
    }

    @Test
    fun computesSha256WithoutLoadingWholeFile() {
        val file: File = temporaryFolder.newFile("sample.bin").apply {
            writeText("hello")
        }
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            ResumableModelDownloader().checksum(file, ModelChecksumAlgorithm.SHA256),
        )
    }

    @Test
    fun resumesFromExistingPartialFile() = runBlocking {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val receivedRange = AtomicReference<String?>()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverThread = thread(name = "range-test-server") {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                var range: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        range = line.substringAfter(':').trim()
                    }
                }
                receivedRange.set(range)
                val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
                val response = payload.copyOfRange(start, payload.size)
                val status = if (start > 0) "206 Partial Content" else "200 OK"
                val contentRange = if (start > 0) {
                    "Content-Range: bytes $start-${payload.lastIndex}/${payload.size}\r\n"
                } else {
                    ""
                }
                val headers = buildString {
                    append("HTTP/1.1 $status\r\n")
                    append("Content-Length: ${response.size}\r\n")
                    append(contentRange)
                    append("Connection: close\r\n\r\n")
                }
                socket.getOutputStream().use { output ->
                    output.write(headers.toByteArray(Charsets.US_ASCII))
                    output.write(response)
                    output.flush()
                }
            }
        }

        try {
            val target = File(temporaryFolder.root, "model.bin")
            val partial = File(temporaryFolder.root, "model.bin.part")
            val initialBytes = 64 * 1024
            partial.writeBytes(payload.copyOfRange(0, initialBytes))
            val checksum = MessageDigest.getInstance("SHA-256")
                .digest(payload)
                .joinToString("") { "%02x".format(it) }
            val artifact = ModelDownloadArtifact(
                id = "test",
                displayName = "测试模型",
                fileName = target.name,
                expectedBytes = payload.size.toLong(),
                checksumAlgorithm = ModelChecksumAlgorithm.SHA256,
                checksum = checksum,
                sources = listOf(
                    ModelDownloadSource("local", "http://127.0.0.1:${server.localPort}/model.bin"),
                ),
            )

            ResumableModelDownloader().download(artifact, target) {}

            assertEquals("bytes=$initialBytes-", receivedRange.get())
            assertArrayEquals(payload, target.readBytes())
            assertFalse(partial.exists())
        } finally {
            server.close()
            serverThread.join(2_000)
        }
    }
}
