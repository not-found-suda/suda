package com.ssafy.mobile.feature.learning.data.local.db

import androidx.room.Entity
import com.ssafy.mobile.feature.learning.domain.model.PendingLearningQuizAnswerSubmission

@Entity(
    tableName = "pending_learning_quiz_answer_submissions",
    primaryKeys = ["sessionId", "questionId"],
)
data class PendingLearningQuizAnswerSubmissionEntity(
    val sessionId: Long,
    val questionId: Long,
    val wordId: Long,
    val recognizedText: String,
    val retryCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastFailureMessage: String?,
)

fun PendingLearningQuizAnswerSubmissionEntity.toDomain(): PendingLearningQuizAnswerSubmission =
    PendingLearningQuizAnswerSubmission(
        sessionId = sessionId,
        questionId = questionId,
        wordId = wordId,
        recognizedText = recognizedText,
        retryCount = retryCount,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        lastFailureMessage = lastFailureMessage,
    )

fun PendingLearningQuizAnswerSubmission.toEntity(): PendingLearningQuizAnswerSubmissionEntity =
    PendingLearningQuizAnswerSubmissionEntity(
        sessionId = sessionId,
        questionId = questionId,
        wordId = wordId,
        recognizedText = recognizedText,
        retryCount = retryCount,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        lastFailureMessage = lastFailureMessage,
    )
