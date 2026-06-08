package com.example.elderhelper.analyzer

data class AnalyzerResult(
    val guidance: String,
    val isSensitive: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSuccess: Boolean
        get() = errorMessage.isNullOrBlank() && guidance.isNotBlank()
}
