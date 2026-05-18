package com.ssafy.mobile.feature.login.data.dto

import com.google.gson.annotations.SerializedName

/**
 * 네이버 OAuth SDK 기반 로그인을 위한 요청 DTO
 */
data class NaverOAuthLoginRequestDto(
    @SerializedName("providerAccessToken")
    val providerAccessToken: String,
)
