package com.ssafy.mobile.feature.learning.domain.model

data class LearningWord(
    val id: Long,
    val word: String,
    val displayText: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
)
