package com.ssafy.mobile.feature.learning.domain.repository

import com.ssafy.mobile.feature.learning.domain.model.LearningQuizAnswerResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizQuestion
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizSession
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizSessionStatus

interface LearningQuizRepository {
    suspend fun createSession(
        categoryId: Long,
        difficulty: String,
    ): Result<LearningQuizSession>

    suspend fun getCurrentQuestion(sessionId: Long): Result<LearningQuizQuestion>

    suspend fun submitAnswer(
        sessionId: Long,
        questionId: Long,
        wordId: Long,
        recognizedText: String,
    ): Result<LearningQuizAnswerResult>

    suspend fun completeSession(sessionId: Long): Result<LearningQuizSessionStatus>

    suspend fun getResult(sessionId: Long): Result<LearningQuizResult>
}
