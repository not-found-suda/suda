package com.ssafy.mobile.feature.learning.domain.model

data class PendingLearningQuizAnswerSubmission(
    val sessionId: Long,
    val questionId: Long,
    val wordId: Long,
    val recognizedText: String,
    val retryCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastFailureMessage: String?,
)
