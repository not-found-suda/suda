package com.ssafy.mobile.feature.learning.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingLearningQuizAnswerSubmissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(submission: PendingLearningQuizAnswerSubmissionEntity)

    @Query(
        """
        SELECT *
        FROM pending_learning_quiz_answer_submissions
        WHERE sessionId = :sessionId AND questionId = :questionId
        LIMIT 1
        """,
    )
    suspend fun find(
        sessionId: Long,
        questionId: Long,
    ): PendingLearningQuizAnswerSubmissionEntity?

    @Query(
        """
        SELECT *
        FROM pending_learning_quiz_answer_submissions
        ORDER BY createdAtMillis ASC
        """,
    )
    suspend fun getAll(): List<PendingLearningQuizAnswerSubmissionEntity>

    @Query(
        """
        DELETE FROM pending_learning_quiz_answer_submissions
        WHERE sessionId = :sessionId AND questionId = :questionId
        """,
    )
    suspend fun delete(
        sessionId: Long,
        questionId: Long,
    )

    @Query(
        """
        UPDATE pending_learning_quiz_answer_submissions
        SET retryCount = retryCount + 1,
            updatedAtMillis = :failedAtMillis,
            lastFailureMessage = :failureMessage
        WHERE sessionId = :sessionId AND questionId = :questionId
        """,
    )
    suspend fun markRetryFailed(
        sessionId: Long,
        questionId: Long,
        failureMessage: String?,
        failedAtMillis: Long,
    )
}
