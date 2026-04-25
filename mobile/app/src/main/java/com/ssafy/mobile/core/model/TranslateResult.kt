package com.ssafy.mobile.core.model

data class SignTranslateResult(
    val correctedText: String,
    val audioUrl: String? = null,
)

data class VoiceTranslateResult(
    val translatedText: String,
)
