package com.ssafy.mobile.feature.learning.domain.model

data class LearningCategory(
    val categoryId: Long,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
)
