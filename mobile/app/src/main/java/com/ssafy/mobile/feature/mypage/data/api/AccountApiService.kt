package com.ssafy.mobile.feature.mypage.data.api

import com.ssafy.mobile.feature.mypage.data.dto.AccountInfoResponseDto
import com.ssafy.mobile.feature.mypage.data.dto.AccountUpdateRequestDto
import com.ssafy.mobile.feature.mypage.data.dto.AccountUpdateResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface AccountApiService {
    @GET("v1/users/me")
    suspend fun getAccountInfo(): Response<AccountInfoResponseDto>

    @PATCH("v1/users/me")
    suspend fun updateAccountInfo(
        @Body request: AccountUpdateRequestDto,
    ): Response<AccountUpdateResponseDto>
}
