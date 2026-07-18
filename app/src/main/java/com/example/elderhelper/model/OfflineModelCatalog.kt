package com.example.elderhelper.model

data class ModelDownloadSource(
    val label: String,
    val url: String,
)

enum class ModelChecksumAlgorithm(val jcaName: String) {
    MD5("MD5"),
    SHA256("SHA-256"),
}

data class ModelDownloadArtifact(
    val id: String,
    val displayName: String,
    val fileName: String,
    val expectedBytes: Long,
    val checksumAlgorithm: ModelChecksumAlgorithm,
    val checksum: String,
    val sources: List<ModelDownloadSource>,
)

object OfflineModelCatalog {
    const val CATALOG_VERSION = 1

    const val ASR_ARCHIVE_BYTES = 77_920_048L
    const val ASR_MODEL_BYTES = 81_828_675L
    const val ASR_TOKENS_BYTES = 75_352L
    const val MINICPM_MODEL_BYTES = 529_101_504L
    const val MINICPM_PROJECTOR_BYTES = 1_108_746_944L

    const val ASR_MODEL_SHA256 = "3ef6c19369b912f7caf3cef8e545c5ccd1a33d9d7ec792a46668dc41c4b229ec"
    const val ASR_TOKENS_SHA256 = "4b2d964e18b9cf139b473003b6698fb2ed9a2a5ec55b93daa677b28f578897aa"

    val asrArchive = ModelDownloadArtifact(
        id = "sherpa-asr-archive",
        displayName = "离线语音包",
        fileName = "sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2",
        expectedBytes = ASR_ARCHIVE_BYTES,
        checksumAlgorithm = ModelChecksumAlgorithm.SHA256,
        checksum = "da92b3db5218c5be53aad53e57d1b6e63e7fc98a0e054fbdd6dbe18e9c6b1450",
        sources = listOf(
            ModelDownloadSource(
                label = "GitHub",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                    "sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2",
            ),
        ),
    )

    val miniCpmModel = ModelDownloadArtifact(
        id = "minicpm-v-4.6-q4",
        displayName = "离线看屏语言模型",
        fileName = ModelAssetManager.MINICPM_MODEL_FILE,
        expectedBytes = MINICPM_MODEL_BYTES,
        checksumAlgorithm = ModelChecksumAlgorithm.MD5,
        checksum = "fd778481dd56b6036dd8f9cf7c1519cf",
        sources = miniCpmSources(ModelAssetManager.MINICPM_MODEL_FILE),
    )

    val miniCpmProjector = ModelDownloadArtifact(
        id = "minicpm-v-4.6-projector",
        displayName = "离线看屏视觉模型",
        fileName = ModelAssetManager.MINICPM_PROJECTOR_FILE,
        expectedBytes = MINICPM_PROJECTOR_BYTES,
        checksumAlgorithm = ModelChecksumAlgorithm.MD5,
        checksum = "54aea6e04d752f47309a48f12795a1a3",
        sources = miniCpmSources(ModelAssetManager.MINICPM_PROJECTOR_FILE),
    )

    val completePackDownloadBytes: Long
        get() = ASR_ARCHIVE_BYTES + MINICPM_MODEL_BYTES + MINICPM_PROJECTOR_BYTES

    private fun miniCpmSources(fileName: String): List<ModelDownloadSource> = listOf(
        ModelDownloadSource(
            label = "ModelScope",
            url = "https://www.modelscope.cn/models/OpenBMB/MiniCPM-V-4.6-gguf/resolve/master/$fileName",
        ),
        ModelDownloadSource(
            label = "Hugging Face",
            url = "https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf/resolve/main/$fileName",
        ),
    )
}
