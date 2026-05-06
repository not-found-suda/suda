package com.ssafy.mobile.feature.learning.data.dto

import com.ssafy.mobile.feature.learning.domain.model.LearningCategory
import kotlinx.serialization.Serializable

@Serializable
data class LearningCategoryDto(
    val categoryId: Long,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
) {
    fun toDomain(): LearningCategory =
        LearningCategory(
            categoryId = categoryId,
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
        )
}

@Serializable
data class LearningCategoriesResponseDto(
    val categories: List<LearningCategoryDto>,
)
