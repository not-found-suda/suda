package com.ssafy.mobile.feature.learning.domain.repository

import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY
import com.ssafy.mobile.feature.learning.domain.model.LearningWord

interface LearningWordRepository {
    suspend fun getWords(
        categoryId: Long,
        difficulty: String = DEFAULT_LEARNING_DIFFICULTY,
    ): Result<List<LearningWord>>
}
