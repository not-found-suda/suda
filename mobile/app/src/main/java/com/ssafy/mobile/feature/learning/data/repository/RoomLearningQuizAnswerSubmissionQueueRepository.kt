package com.ssafy.mobile.feature.learning.data.repository

import com.ssafy.mobile.feature.learning.data.local.db.PendingLearningQuizAnswerSubmissionDao
import com.ssafy.mobile.feature.learning.data.local.db.toDomain
import com.ssafy.mobile.feature.learning.data.local.db.toEntity
import com.ssafy.mobile.feature.learning.domain.model.PendingLearningQuizAnswerSubmission
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizAnswerSubmissionQueueRepository
import javax.inject.Inject

class RoomLearningQuizAnswerSubmissionQueueRepository
    @Inject
    constructor(
        private val dao: PendingLearningQuizAnswerSubmissionDao,
    ) : LearningQuizAnswerSubmissionQueueRepository {
        override suspend fun enqueueAnswerSubmission(
            submission: PendingLearningQuizAnswerSubmission,
        ) {
            val existing =
                dao.find(
                    sessionId = submission.sessionId,
                    questionId = submission.questionId,
                )
            val entity =
                existing
                    ?.copy(
                        wordId = submission.wordId,
                        recognizedText = submission.recognizedText,
                        updatedAtMillis = submission.updatedAtMillis,
                        lastFailureMessage = submission.lastFailureMessage,
                    ) ?: submission.toEntity()

            dao.upsert(entity)
        }

        override suspend fun getPendingAnswerSubmissions(): List<
            PendingLearningQuizAnswerSubmission,
        > =
            dao.getAll().map { entity -> entity.toDomain() }

        override suspend fun deleteAnswerSubmission(
            sessionId: Long,
            questionId: Long,
        ) {
            dao.delete(
                sessionId = sessionId,
                questionId = questionId,
            )
        }

        override suspend fun markAnswerSubmissionRetryFailed(
            sessionId: Long,
            questionId: Long,
            failureMessage: String?,
            failedAtMillis: Long,
        ) {
            dao.markRetryFailed(
                sessionId = sessionId,
                questionId = questionId,
                failureMessage = failureMessage,
                failedAtMillis = failedAtMillis,
            )
        }
    }
