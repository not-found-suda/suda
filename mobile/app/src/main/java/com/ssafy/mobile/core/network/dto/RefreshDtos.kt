package com.ssafy.mobile.core.network.dto

import com.google.gson.annotations.SerializedName

data class RefreshRequestDto(
    @SerializedName("refreshToken")
    val refreshToken: String,
)

data class RefreshResponseDto(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String,
    @SerializedName("tokenType")
    val tokenType: String,
    @SerializedName("expiresIn")
    val expiresIn: Long,
)
