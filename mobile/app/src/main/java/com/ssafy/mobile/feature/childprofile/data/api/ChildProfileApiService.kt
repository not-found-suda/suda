package com.ssafy.mobile.feature.childprofile.data.api

import com.ssafy.mobile.feature.childprofile.data.dto.ChildProfileCreateRequestDto
import com.ssafy.mobile.feature.childprofile.data.dto.ChildProfileListResponseDto
import com.ssafy.mobile.feature.childprofile.data.dto.ChildProfileResponseDto
import com.ssafy.mobile.feature.childprofile.data.dto.ChildProfileUpdateRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ChildProfileApiService {
    @GET("v1/children")
    suspend fun getChildProfiles(): Response<ChildProfileListResponseDto>

    @POST("v1/children")
    suspend fun createChildProfile(
        @Body request: ChildProfileCreateRequestDto,
    ): Response<ChildProfileResponseDto>

    @GET("v1/children/{childId}")
    suspend fun getChildProfile(
        @Path("childId") childId: Long,
    ): Response<ChildProfileResponseDto>

    @PATCH("v1/children/{childId}")
    suspend fun updateChildProfile(
        @Path("childId") childId: Long,
        @Body request: ChildProfileUpdateRequestDto,
    ): Response<ChildProfileResponseDto>

    @DELETE("v1/children/{childId}")
    suspend fun deleteChildProfile(
        @Path("childId") childId: Long,
    ): Response<Unit>
}
