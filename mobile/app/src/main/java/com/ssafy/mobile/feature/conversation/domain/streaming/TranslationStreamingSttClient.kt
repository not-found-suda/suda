package com.ssafy.mobile.feature.conversation.domain.streaming

interface TranslationStreamingSttClient {
    fun start(
        sessionId: Long,
        locale: String = DEFAULT_LOCALE,
        listener: Listener,
    ): TranslationStreamingSttConnection

    interface Listener {
        fun onEvent(event: TranslationStreamingSttEvent)
    }

    companion object {
        const val DEFAULT_LOCALE = "ko-KR"
    }
}

interface TranslationStreamingSttConnection {
    fun sendAudio(audioBytes: ByteArray): Boolean

    fun end(): Boolean

    fun close()
}

sealed interface TranslationStreamingSttEvent {
    data object Started : TranslationStreamingSttEvent

    data object Configured : TranslationStreamingSttEvent

    data class Partial(
        val recognizedText: String,
        val correctedText: String,
        val confidence: Double?,
        val locale: String?,
    ) : TranslationStreamingSttEvent

    data class Final(
        val recognizedText: String,
        val correctedText: String,
        val confidence: Double?,
        val locale: String?,
    ) : TranslationStreamingSttEvent

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : TranslationStreamingSttEvent

    data object Closed : TranslationStreamingSttEvent
}
