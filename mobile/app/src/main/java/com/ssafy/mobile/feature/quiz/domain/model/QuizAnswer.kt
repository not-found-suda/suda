package com.ssafy.mobile.feature.quiz.domain.model

data class QuizAnswer(
    val questionId: Long,
    val sttText: String,
    val star: Int,
    val attemptCount: Int,
    val isCorrect: Boolean = star >= PASSING_STAR,
    val feedback: String? = null,
)

private const val PASSING_STAR = 3
