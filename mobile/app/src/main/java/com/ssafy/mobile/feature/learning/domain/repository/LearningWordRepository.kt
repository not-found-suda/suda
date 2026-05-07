package com.ssafy.mobile.feature.learning.domain.repository

import com.ssafy.mobile.feature.learning.domain.model.LearningWord

interface LearningWordRepository {
    suspend fun getWords(categoryId: Long): Result<List<LearningWord>>
}
