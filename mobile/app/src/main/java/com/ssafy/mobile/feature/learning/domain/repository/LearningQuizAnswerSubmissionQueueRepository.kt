package com.ssafy.mobile.feature.learning.domain.repository

import com.ssafy.mobile.feature.learning.domain.model.PendingLearningQuizAnswerSubmission

interface LearningQuizAnswerSubmissionQueueRepository {
    suspend fun enqueueAnswerSubmission(submission: PendingLearningQuizAnswerSubmission)

    suspend fun getPendingAnswerSubmissions(): List<PendingLearningQuizAnswerSubmission>

    suspend fun deleteAnswerSubmission(
        sessionId: Long,
        questionId: Long,
    )

    suspend fun markAnswerSubmissionRetryFailed(
        sessionId: Long,
        questionId: Long,
        failureMessage: String?,
        failedAtMillis: Long,
    )
}
