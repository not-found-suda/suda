package com.ssafy.mobile.core.auth

interface TokenStorage {
    fun getAccessToken(): String?

    fun getRefreshToken(): String?

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
    )

    fun updateAccessToken(accessToken: String)

    fun clearTokens()
}
