package com.example.elderhelper.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class OfflineModelSnapshot(
    val asrReady: Boolean,
    val screenModelReady: Boolean,
    val installedBytes: Long,
) {
    val complete: Boolean
        get() = asrReady && screenModelReady
}

data class OfflineModelProgress(
    val message: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
}

class OfflineModelManager(
    context: Context,
    private val downloader: ResumableModelDownloader = ResumableModelDownloader(),
) {
    private val appContext = context.applicationContext
    private val assets = ModelAssetManager(appContext)
    private val downloadsDir = File(appContext.filesDir, "models/.downloads")

    fun snapshot(): OfflineModelSnapshot = OfflineModelSnapshot(
        asrReady = isAsrReady(),
        screenModelReady = isScreenModelReady(),
        installedBytes = installedBytes(),
    )

    /** Deletes only the app-private offline-model directories after an explicit UI confirmation. */
    suspend fun removeCompletePack() = withContext(Dispatchers.IO) {
        listOf(
            assets.sherpaAsrModelDir(),
            assets.miniCpmModelDir(),
            downloadsDir,
        ).forEach(::deleteAppPrivateModelDirectory)
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CATALOG_VERSION)
            .apply()
    }

    fun requiredFreeBytes(): Long {
        var required = DOWNLOAD_SAFETY_BYTES
        if (!isAsrReady()) {
            required += OfflineModelCatalog.ASR_ARCHIVE_BYTES + OfflineModelCatalog.ASR_MODEL_BYTES
        }
        val miniCpm = assets.miniCpmModelFiles()
        if (!hasExpectedSize(miniCpm.model, OfflineModelCatalog.MINICPM_MODEL_BYTES)) {
            required += OfflineModelCatalog.MINICPM_MODEL_BYTES
        }
        if (!hasExpectedSize(miniCpm.projector, OfflineModelCatalog.MINICPM_PROJECTOR_BYTES)) {
            required += OfflineModelCatalog.MINICPM_PROJECTOR_BYTES
        }
        return required
    }

    fun availableBytes(): Long = StatFs(appContext.filesDir.absolutePath).availableBytes

    fun hasEnoughStorage(): Boolean = availableBytes() >= requiredFreeBytes()

    fun hasNetworkConnection(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isActiveNetworkMetered(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.isActiveNetworkMetered
    }

    suspend fun installCompletePack(onProgress: (OfflineModelProgress) -> Unit) = withContext(Dispatchers.IO) {
        if (!hasEnoughStorage()) {
            throw IOException("存储空间不足，至少还需要 ${formatBytes(requiredFreeBytes())} 可用空间。")
        }

        val neededArtifacts = buildList {
            if (!isAsrReady()) add(OfflineModelCatalog.asrArchive)
            val miniCpm = assets.miniCpmModelFiles()
            if (!hasExpectedSize(miniCpm.model, OfflineModelCatalog.MINICPM_MODEL_BYTES)) {
                add(OfflineModelCatalog.miniCpmModel)
            }
            if (!hasExpectedSize(miniCpm.projector, OfflineModelCatalog.MINICPM_PROJECTOR_BYTES)) {
                add(OfflineModelCatalog.miniCpmProjector)
            }
        }
        val totalBytes = neededArtifacts.sumOf { it.expectedBytes }.coerceAtLeast(1L)
        var completedBytes = 0L

        if (!isAsrReady()) {
            val artifact = OfflineModelCatalog.asrArchive
            val archive = File(downloadsDir, artifact.fileName)
            downloader.download(artifact, archive) { fileProgress ->
                onProgress(
                    OfflineModelProgress(
                        message = "正在下载${artifact.displayName}：${fileProgress.source} " +
                            "${formatBytes(fileProgress.downloadedBytes)} / ${formatBytes(fileProgress.totalBytes)}",
                        downloadedBytes = completedBytes + fileProgress.downloadedBytes,
                        totalBytes = totalBytes,
                    ),
                )
            }
            onProgress(OfflineModelProgress("正在安装离线语音包", completedBytes + artifact.expectedBytes, totalBytes))
            installAsrArchive(archive)
            archive.delete()
            completedBytes += artifact.expectedBytes
        }

        val miniCpm = assets.miniCpmModelFiles()
        completedBytes = downloadIfNeeded(
            artifact = OfflineModelCatalog.miniCpmModel,
            target = miniCpm.model,
            completedBefore = completedBytes,
            totalBytes = totalBytes,
            onProgress = onProgress,
        )
        completedBytes = downloadIfNeeded(
            artifact = OfflineModelCatalog.miniCpmProjector,
            target = miniCpm.projector,
            completedBefore = completedBytes,
            totalBytes = totalBytes,
            onProgress = onProgress,
        )

        if (!snapshot().complete) {
            throw IOException("离线模型安装后校验未通过，请重试。")
        }
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CATALOG_VERSION, OfflineModelCatalog.CATALOG_VERSION)
            .apply()
        onProgress(OfflineModelProgress("完整离线包已准备好", totalBytes, totalBytes))
    }

    private suspend fun downloadIfNeeded(
        artifact: ModelDownloadArtifact,
        target: File,
        completedBefore: Long,
        totalBytes: Long,
        onProgress: (OfflineModelProgress) -> Unit,
    ): Long {
        if (hasExpectedSize(target, artifact.expectedBytes)) return completedBefore
        downloader.download(artifact, target) { fileProgress ->
            onProgress(
                OfflineModelProgress(
                    message = "正在下载${artifact.displayName}：${fileProgress.source} " +
                        "${formatBytes(fileProgress.downloadedBytes)} / ${formatBytes(fileProgress.totalBytes)}",
                    downloadedBytes = completedBefore + fileProgress.downloadedBytes,
                    totalBytes = totalBytes,
                ),
            )
        }
        return completedBefore + artifact.expectedBytes
    }

    private fun installAsrArchive(archive: File) {
        val asrDir = assets.sherpaAsrModelDir().apply { mkdirs() }
        val modelTarget = File(asrDir, "model.int8.onnx")
        val tokensTarget = File(asrDir, "tokens.txt")
        val destinations = mapOf(
            "model.int8.onnx" to ExtractedFile(
                target = modelTarget,
                expectedBytes = OfflineModelCatalog.ASR_MODEL_BYTES,
                checksum = OfflineModelCatalog.ASR_MODEL_SHA256,
            ),
            "tokens.txt" to ExtractedFile(
                target = tokensTarget,
                expectedBytes = OfflineModelCatalog.ASR_TOKENS_BYTES,
                checksum = OfflineModelCatalog.ASR_TOKENS_SHA256,
            ),
        )
        destinations.values.forEach { it.partial.delete() }

        BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)), true).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    if (!entry.isFile) continue
                    val destination = destinations.entries.firstOrNull { (name, _) ->
                        entry.name == name || entry.name.endsWith("/$name")
                    }?.value ?: continue
                    FileOutputStream(destination.partial, false).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = tar.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
            }
        }

        destinations.values.forEach { extracted ->
            if (extracted.partial.length() != extracted.expectedBytes) {
                throw IOException("${extracted.target.name} 解压后大小不正确。")
            }
            val actual = downloader.checksum(extracted.partial, ModelChecksumAlgorithm.SHA256)
            if (!actual.equals(extracted.checksum, ignoreCase = true)) {
                extracted.partial.delete()
                throw IOException("${extracted.target.name} 解压后校验失败。")
            }
        }
        destinations.values.forEach { extracted ->
            if (extracted.target.exists()) extracted.target.delete()
            if (!extracted.partial.renameTo(extracted.target)) {
                extracted.partial.copyTo(extracted.target, overwrite = true)
                extracted.partial.delete()
            }
        }
    }

    private fun isAsrReady(): Boolean {
        val dir = assets.sherpaAsrModelDir()
        return hasExpectedSize(File(dir, "model.int8.onnx"), OfflineModelCatalog.ASR_MODEL_BYTES) &&
            hasExpectedSize(File(dir, "tokens.txt"), OfflineModelCatalog.ASR_TOKENS_BYTES)
    }

    private fun isScreenModelReady(): Boolean {
        val files = assets.miniCpmModelFiles()
        return hasExpectedSize(files.model, OfflineModelCatalog.MINICPM_MODEL_BYTES) &&
            hasExpectedSize(files.projector, OfflineModelCatalog.MINICPM_PROJECTOR_BYTES)
    }

    private fun hasExpectedSize(file: File, expectedBytes: Long): Boolean =
        file.isFile && file.length() == expectedBytes

    private fun installedBytes(): Long = listOf(
        assets.sherpaAsrModelDir(),
        assets.miniCpmModelDir(),
    ).sumOf(::directoryBytes)

    private fun directoryBytes(directory: File): Long =
        directory.walkTopDown().filter(File::isFile).sumOf(File::length)

    private fun deleteAppPrivateModelDirectory(directory: File) {
        if (!directory.exists()) return
        val root = appContext.filesDir.canonicalFile
        val target = directory.canonicalFile
        require(target.path.startsWith(root.path + File.separator)) {
            "Refusing to delete a directory outside app-private storage."
        }
        if (!target.deleteRecursively()) {
            throw IOException("无法删除离线模型文件，请稍后重试。")
        }
    }

    private data class ExtractedFile(
        val target: File,
        val expectedBytes: Long,
        val checksum: String,
    ) {
        val partial: File
            get() = File(target.parentFile, "${target.name}.installing")
    }

    companion object {
        private const val BUFFER_BYTES = 64 * 1024
        private const val DOWNLOAD_SAFETY_BYTES = 512L * 1024L * 1024L
        private const val PREFERENCES_NAME = "offline_models"
        private const val KEY_CATALOG_VERSION = "catalog_version"

        fun formatBytes(bytes: Long): String {
            val gib = bytes / (1024.0 * 1024.0 * 1024.0)
            return if (gib >= 1.0) "%.1f GB".format(gib) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
