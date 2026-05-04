package com.ssafy.mobile.core.network

import com.ssafy.mobile.core.auth.TokenStorage
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor
    @Inject
    constructor(
        private val tokenStorage: TokenStorage,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            // 이미 Authorization 헤더가 있는 경우(예: 외부 API, 토큰 갱신 API 자체) 덮어쓰지 않음
            if (request.header("Authorization") != null) {
                return chain.proceed(request)
            }

            val accessToken = tokenStorage.getAccessToken()
            val builder = request.newBuilder()

            if (!accessToken.isNullOrEmpty()) {
                builder.addHeader("Authorization", "Bearer $accessToken")
            }

            return chain.proceed(builder.build())
        }
    }
