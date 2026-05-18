package com.ssafy.mobile.feature.learning.data.api

import com.ssafy.mobile.feature.learning.data.dto.LearningCategoriesResponseDto
import retrofit2.http.GET

interface LearningCategoryApiService {
    @GET("v1/learn/categories")
    suspend fun getCategories(): LearningCategoriesResponseDto
}
