package com.ssafy.mobile.translation

data class TranslationResult(
    val glossText: String,
    val koreanText: String,
    val rawText: String,
    val usedRuleBased: Boolean,
    val elapsedMs: Long,
)
