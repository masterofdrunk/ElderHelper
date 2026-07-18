package com.example.elderhelper.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class ModelFileProgress(
    val artifact: ModelDownloadArtifact,
    val source: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
)

class ResumableModelDownloader {
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun cancelActiveDownload() {
        activeConnection?.disconnect()
    }

    suspend fun download(
        artifact: ModelDownloadArtifact,
        targetFile: File,
        onProgress: (ModelFileProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        val partialFile = File(targetFile.parentFile, "${targetFile.name}.part")

        if (targetFile.exists()) {
            if (isValid(targetFile, artifact)) {
                partialFile.delete()
                onProgress(ModelFileProgress(artifact, "本地", artifact.expectedBytes, artifact.expectedBytes))
                return@withContext targetFile
            }
            targetFile.delete()
        }

        val failures = mutableListOf<String>()
        for (source in artifact.sources) {
            try {
                downloadFromSource(artifact, source, partialFile, onProgress)
                if (partialFile.length() != artifact.expectedBytes) {
                    throw IOException(
                        "${artifact.displayName} 文件大小不正确：${partialFile.length()} / ${artifact.expectedBytes}",
                    )
                }
                if (!checksum(partialFile, artifact.checksumAlgorithm)
                        .equals(artifact.checksum, ignoreCase = true)
                ) {
                    partialFile.delete()
                    throw IOException("${artifact.displayName} 校验失败，请重新下载。")
                }
                promote(partialFile, targetFile)
                return@withContext targetFile
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                failures += "${source.label}: ${error.message ?: error::class.java.simpleName}"
            }
        }
        throw IOException("${artifact.displayName} 下载失败。${failures.joinToString("；")}")
    }

    private suspend fun downloadFromSource(
        artifact: ModelDownloadArtifact,
        source: ModelDownloadSource,
        partialFile: File,
        onProgress: (ModelFileProgress) -> Unit,
    ) {
        val resumeFrom = partialFile.takeIf { it.exists() }?.length() ?: 0L
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ElderHelper/${OfflineModelCatalog.CATALOG_VERSION}")
            if (resumeFrom > 0L) {
                setRequestProperty("Range", "bytes=$resumeFrom-")
            }
        }
        activeConnection = connection

        try {
            val responseCode = connection.responseCode
            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE) {
                val remoteTotal = parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                if (remoteTotal == partialFile.length() && remoteTotal == artifact.expectedBytes) {
                    return
                }
                partialFile.delete()
                throw IOException("服务器无法继续上次下载，已重置进度。")
            }

            val append = responseCode == HttpURLConnection.HTTP_PARTIAL && resumeFrom > 0L
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("HTTP $responseCode")
            }
            if (!append && resumeFrom > 0L) {
                partialFile.delete()
            }

            val startBytes = if (append) resumeFrom else 0L
            connection.inputStream.use { input ->
                FileOutputStream(partialFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var downloaded = startBytes
                    var lastUpdate = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                            lastUpdate = now
                            onProgress(
                                ModelFileProgress(
                                    artifact = artifact,
                                    source = source.label,
                                    downloadedBytes = downloaded,
                                    totalBytes = artifact.expectedBytes,
                                ),
                            )
                        }
                    }
                    output.flush()
                    output.fd.sync()
                    onProgress(
                        ModelFileProgress(
                            artifact = artifact,
                            source = source.label,
                            downloadedBytes = downloaded,
                            totalBytes = artifact.expectedBytes,
                        ),
                    )
                }
            }
        } finally {
            connection.disconnect()
            if (activeConnection === connection) activeConnection = null
        }
    }

    internal fun isValid(file: File, artifact: ModelDownloadArtifact): Boolean {
        if (!file.isFile || file.length() != artifact.expectedBytes) return false
        return checksum(file, artifact.checksumAlgorithm).equals(artifact.checksum, ignoreCase = true)
    }

    internal fun checksum(file: File, algorithm: ModelChecksumAlgorithm): String {
        val digest = MessageDigest.getInstance(algorithm.jcaName)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun promote(partialFile: File, targetFile: File) {
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("无法替换旧模型文件：${targetFile.name}")
        }
        if (!partialFile.renameTo(targetFile)) {
            partialFile.copyTo(targetFile, overwrite = true)
            if (!partialFile.delete()) partialFile.deleteOnExit()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val BUFFER_BYTES = 64 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416

        internal fun parseContentRangeTotal(header: String?): Long? {
            if (header.isNullOrBlank()) return null
            val slash = header.lastIndexOf('/')
            if (slash < 0 || slash == header.lastIndex) return null
            return header.substring(slash + 1).trim().toLongOrNull()
        }
    }
}
