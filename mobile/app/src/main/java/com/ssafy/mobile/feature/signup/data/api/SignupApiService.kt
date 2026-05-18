package com.ssafy.mobile.feature.signup.data.api

import com.ssafy.mobile.feature.signup.data.dto.SignupRequestDto
import com.ssafy.mobile.feature.signup.data.dto.SignupResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SignupApiService {
    @POST("v1/auth/signup")
    suspend fun signup(
        @Body request: SignupRequestDto,
    ): Response<SignupResponseDto>
}
