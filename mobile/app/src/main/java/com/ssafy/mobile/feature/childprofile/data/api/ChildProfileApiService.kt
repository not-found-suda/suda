package com.ssafy.mobile.feature.childprofile.data.api

import com.ssafy.mobile.feature.childprofile.data.dto.ChildProfileCreateRequestDto
import com.ssafy.mobile.feature.childprofile.data.dto.ChildProfileResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ChildProfileApiService {
    @GET("v1/children")
    suspend fun getChildProfiles(): Response<List<ChildProfileResponseDto>>

    @POST("v1/children")
    suspend fun createChildProfile(
        @Body request: ChildProfileCreateRequestDto,
    ): Response<ChildProfileResponseDto>
}
