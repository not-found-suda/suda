package com.ssafy.mobile.feature.learning.domain.model

sealed interface LearningQuizAnswerSubmissionSyncEvent {
    data class AnswerSynced(
        val result: LearningQuizAnswerResult,
    ) : LearningQuizAnswerSubmissionSyncEvent

    data class AnswerAcceptedWithoutResult(
        val sessionId: Long,
        val questionId: Long,
    ) : LearningQuizAnswerSubmissionSyncEvent
}
