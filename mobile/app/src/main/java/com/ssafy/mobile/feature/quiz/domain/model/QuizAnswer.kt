package com.ssafy.mobile.feature.quiz.domain.model

import java.io.File

data class QuizAnswer(
    val questionId: Long,
    val sttText: String,
    val star: Int? = null,
    val attemptCount: Int,
    val isCorrect: Boolean? = star?.let { it >= PASSING_STAR },
    val feedback: String? = null,
    val audioFile: File? = null,
    val audioMimeType: String? = null,
) {
    val isScored: Boolean
        get() = star != null && isCorrect != null
}

private const val PASSING_STAR = 3
