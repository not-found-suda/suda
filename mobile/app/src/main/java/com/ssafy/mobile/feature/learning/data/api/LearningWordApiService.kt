package com.ssafy.mobile.feature.learning.data.api

import com.ssafy.mobile.feature.learning.data.dto.LearningWordsResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LearningWordApiService {
    @GET("v1/learn/words")
    suspend fun getWords(
        @Query("categoryId") categoryId: Long,
        @Query("difficulty") difficulty: String,
    ): Response<LearningWordsResponseDto>
}
