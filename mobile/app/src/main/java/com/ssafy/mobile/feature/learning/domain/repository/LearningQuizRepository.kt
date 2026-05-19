package com.ssafy.mobile.feature.learning.domain.repository

import com.ssafy.mobile.feature.learning.domain.model.LearningQuizAnswerResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizQuestion
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizSession
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizSessionStatus
import java.io.File

interface LearningQuizRepository {
    suspend fun createSession(
        childProfileId: Long,
        categoryId: Long,
        difficulty: String,
        totalQuestionCount: Int,
    ): Result<LearningQuizSession>

    suspend fun getCurrentQuestion(sessionId: Long): Result<LearningQuizQuestion>

    suspend fun submitAnswer(
        sessionId: Long,
        questionId: Long,
        audioFile: File?,
        audioMimeType: String?,
    ): Result<LearningQuizAnswerResult>

    suspend fun completeSession(sessionId: Long): Result<LearningQuizSessionStatus>

    suspend fun getResult(sessionId: Long): Result<LearningQuizResult>
}
