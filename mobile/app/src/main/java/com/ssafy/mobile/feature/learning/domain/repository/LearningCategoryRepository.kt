package com.ssafy.mobile.feature.learning.domain.repository

import com.ssafy.mobile.feature.learning.domain.model.LearningCategory

interface LearningCategoryRepository {
    suspend fun getCategories(): List<LearningCategory>
}
