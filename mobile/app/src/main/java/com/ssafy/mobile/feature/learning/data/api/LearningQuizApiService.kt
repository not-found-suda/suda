package com.ssafy.mobile.feature.learning.data.api

import com.ssafy.mobile.feature.learning.data.dto.LearningQuizAnswerRequestDto
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizAnswerResponseDto
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizCurrentQuestionResponseDto
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizResultResponseDto
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizSessionRequestDto
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizSessionResponseDto
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizSessionStatusRequestDto
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizSessionStatusResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface LearningQuizApiService {
    @POST("v1/learn/quizzes/sessions")
    suspend fun createSession(
        @Body request: LearningQuizSessionRequestDto,
    ): Response<LearningQuizSessionResponseDto>

    @GET("v1/learn/quizzes/sessions/{sessionId}/questions/current")
    suspend fun getCurrentQuestion(
        @Path("sessionId") sessionId: Long,
    ): Response<LearningQuizCurrentQuestionResponseDto>

    @POST("v1/learn/quizzes/sessions/{sessionId}/answers")
    suspend fun submitAnswer(
        @Path("sessionId") sessionId: Long,
        @Body request: LearningQuizAnswerRequestDto,
    ): Response<LearningQuizAnswerResponseDto>

    @PATCH("v1/learn/quizzes/sessions/{sessionId}")
    suspend fun updateSessionStatus(
        @Path("sessionId") sessionId: Long,
        @Body request: LearningQuizSessionStatusRequestDto,
    ): Response<LearningQuizSessionStatusResponseDto>

    @GET("v1/learn/quizzes/sessions/{sessionId}/result")
    suspend fun getResult(
        @Path("sessionId") sessionId: Long,
    ): Response<LearningQuizResultResponseDto>
}
