package com.ssafy.mobile.feature.login.data.oauth

import android.content.Context
import com.navercorp.nid.NaverIdLoginSDK
import com.ssafy.mobile.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네이버 Android SDK 초기화 및 상태 관리를 담당하는 매니저 클래스입니다.
 */
@Singleton
class NaverOAuthManager
    @Inject
    constructor() {
        /**
         * SDK 초기화에 필요한 설정값이 유효한지 확인합니다.
         */
        val isConfigValid: Boolean
            get() =
                BuildConfig.NAVER_CLIENT_ID.isNotBlank() &&
                    BuildConfig.NAVER_CLIENT_ID != "null" &&
                    BuildConfig.NAVER_CLIENT_SECRET.isNotBlank() &&
                    BuildConfig.NAVER_CLIENT_SECRET != "null" &&
                    BuildConfig.NAVER_CLIENT_NAME.isNotBlank() &&
                    BuildConfig.NAVER_CLIENT_NAME != "null"

        /**
         * 네이버 SDK를 초기화합니다.
         */
        fun initialize(context: Context) {
            if (!isConfigValid) return

            NaverIdLoginSDK.initialize(
                context = context,
                clientId = BuildConfig.NAVER_CLIENT_ID,
                clientSecret = BuildConfig.NAVER_CLIENT_SECRET,
                clientName = BuildConfig.NAVER_CLIENT_NAME,
            )
        }

        /**
         * 현재 저장된 액세스 토큰을 가져옵니다.
         */
        fun getAccessToken(): String? = NaverIdLoginSDK.getAccessToken()

        /**
         * 로그아웃 처리를 수행합니다 (로컬 토큰 삭제).
         */
        fun logout() {
            NaverIdLoginSDK.logout(
                object : com.navercorp.nid.oauth.util.NidOAuthCallback {
                    override fun onSuccess() = Unit

                    override fun onFailure(
                        errorCode: String,
                        message: String,
                    ) = Unit
                },
            )
        }
    }
