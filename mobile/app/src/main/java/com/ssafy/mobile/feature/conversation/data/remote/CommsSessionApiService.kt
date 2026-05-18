package com.ssafy.mobile.feature.conversation.data.remote

import com.ssafy.mobile.feature.conversation.data.remote.model.CommsSessionCreateRequest
import com.ssafy.mobile.feature.conversation.data.remote.model.CommsSessionResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface CommsSessionApiService {
    @POST("v1/comms/sessions")
    suspend fun createSession(
        @Body request: CommsSessionCreateRequest,
    ): Response<CommsSessionResponseDto>

    @PATCH("v1/comms/sessions/{sessionId}/end")
    suspend fun endSession(
        @Path("sessionId") sessionId: Long,
    ): Response<CommsSessionResponseDto>
}
