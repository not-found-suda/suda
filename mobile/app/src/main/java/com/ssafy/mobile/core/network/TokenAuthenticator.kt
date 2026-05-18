package com.ssafy.mobile.core.network

import com.ssafy.mobile.core.auth.AuthSessionManager
import com.ssafy.mobile.core.auth.TokenStorage
import java.net.HttpURLConnection
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator
    @Inject
    constructor(
        private val tokenStorage: TokenStorage,
        private val authSessionManager: AuthSessionManager,
        private val refreshTokenClient: RefreshTokenClient,
    ) : Authenticator {
        companion object {
            private const val MAX_RETRY_COUNT = 3
        }

        private val mutex = Mutex()

        private fun responseCount(response: Response?): Int {
            var result = 1
            var prior = response?.priorResponse
            while (prior != null) {
                result++
                prior = prior.priorResponse
            }
            return result
        }

        @Suppress("ReturnCount")
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            // 방어 코드
            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return null

            // 무한 루프 방지: 재시도가 MAX_RETRY_COUNT를 초과하면 포기
            if (responseCount(response) >= MAX_RETRY_COUNT) {
                return null
            }

            val requestAccessToken =
                response.request
                    .header(
                        "Authorization",
                    )?.removePrefix("Bearer ")

            return runBlocking {
                // 다중 API 호출 시 Refresh 가 중복해서 여러 번 일어나는 것을 방지 (Mutex 잠금)
                mutex.withLock {
                    val currentAccessToken = tokenStorage.getAccessToken()

                    // 1. 내가 보낸 토큰과 현재 저장된 토큰이 다르면,
                    // 다른 스레드(요청)에서 이미 갱신을 완료한 것임. 갱신된 최신 토큰으로 재시도.
                    if (requestAccessToken != currentAccessToken &&
                        !currentAccessToken.isNullOrEmpty()
                    ) {
                        return@runBlocking response.request
                            .newBuilder()
                            .header("Authorization", "Bearer $currentAccessToken")
                            .build()
                    }

                    val refreshToken = tokenStorage.getRefreshToken()

                    // 2. Refresh Token 자체가 없으면 더 이상 갱신 불가 (로그아웃 처리 필요)
                    if (refreshToken.isNullOrEmpty()) {
                        authSessionManager.clearSession()
                        return@runBlocking null
                    }

                    // 3. Refresh API 호출 (동기적으로 대기)
                    val refreshResponse = refreshTokenClient.refresh(refreshToken)

                    if (refreshResponse != null) {
                        // 성공: 새 토큰들 저장 후 기존 실패했던 요청 재시도
                        try {
                            tokenStorage.clearTokens()
                            tokenStorage.saveTokens(
                                accessToken = refreshResponse.accessToken,
                                refreshToken = refreshResponse.refreshToken,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IllegalArgumentException) {
                            android.util.Log.e("TokenAuthenticator", "Invalid token format", e)
                            authSessionManager.clearSession()
                            return@runBlocking null
                        } catch (e: java.security.GeneralSecurityException) {
                            android.util.Log.e("TokenAuthenticator", "Security storage error", e)
                            authSessionManager.clearSession()
                            return@runBlocking null
                        }

                        response.request
                            .newBuilder()
                            .header("Authorization", "Bearer ${refreshResponse.accessToken}")
                            .build()
                    } else {
                        // 실패: 토큰 파기 및 인증 만료 처리
                        authSessionManager.clearSession()
                        null
                    }
                }
            }
        }
    }
