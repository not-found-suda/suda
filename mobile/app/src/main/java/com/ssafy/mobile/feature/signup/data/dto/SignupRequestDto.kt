package com.ssafy.mobile.feature.signup.data.dto

import com.google.gson.annotations.SerializedName

data class SignupRequestDto(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("name")
    val name: String,
)
