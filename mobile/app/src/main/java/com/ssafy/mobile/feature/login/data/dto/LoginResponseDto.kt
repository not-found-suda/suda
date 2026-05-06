package com.ssafy.mobile.feature.login.data.dto

import com.google.gson.annotations.SerializedName

data class LoginResponseDto(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("tokenType")
    val tokenType: String,
    @SerializedName("expiresIn")
    val expiresIn: Long,
)
