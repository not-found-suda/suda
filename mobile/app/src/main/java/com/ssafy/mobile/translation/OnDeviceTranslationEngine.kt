package com.ssafy.mobile.translation

interface OnDeviceTranslationEngine {
    val debugModelAssetPath: String
    val debugPreparedModelPath: String?
    val debugPreparedEntryNames: List<String>
    val debugMaxTokens: Int
    val debugTopK: Int
    val debugBackendSummary: String

    suspend fun load()

    suspend fun translate(
        glossText: String,
        sentenceType: String? = null,
    ): TranslationResult

    fun close()
}
