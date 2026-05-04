package com.ssafy.mobile.feature.quiz.domain.model

data class QuizAnswer(
    val questionId: Long,
    val sttText: String,
    val star: Int,
    val attemptCount: Int,
)
