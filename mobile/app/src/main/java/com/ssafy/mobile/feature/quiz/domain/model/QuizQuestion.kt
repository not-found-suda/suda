package com.ssafy.mobile.feature.quiz.domain.model

data class QuizQuestion(
    val id: Long,
    val wordId: Long,
    val categoryId: Long,
    val word: String,
    val imageUrl: String? = null,
)
