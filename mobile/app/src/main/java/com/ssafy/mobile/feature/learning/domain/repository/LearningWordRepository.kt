package com.ssafy.mobile.feature.learning.domain.repository

import com.ssafy.mobile.feature.learning.domain.model.LearningWord

interface LearningWordRepository {
    suspend fun getWords(
        categoryId: Long,
        difficulty: String = DEFAULT_DIFFICULTY,
    ): Result<List<LearningWord>>
}

private const val DEFAULT_DIFFICULTY = "EASY"
