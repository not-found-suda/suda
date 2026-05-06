package com.ssafy.mobile.feature.login.data.api

import com.ssafy.mobile.feature.login.data.dto.LoginRequestDto
import com.ssafy.mobile.feature.login.data.dto.LoginResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LoginApiService {
    @POST("v1/auth/login")
    suspend fun login(
        @Body request: LoginRequestDto,
    ): Response<LoginResponseDto>
}
