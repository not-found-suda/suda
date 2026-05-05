package com.ssafy.mobile.feature.signup.data.dto

import com.google.gson.annotations.SerializedName

data class SignupResponseDto(
    @SerializedName("userId")
    val userId: Long,
    @SerializedName("email")
    val email: String,
)
