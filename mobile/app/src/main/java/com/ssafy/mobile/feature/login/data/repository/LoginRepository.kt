package com.ssafy.mobile.feature.login.data.repository

import com.ssafy.mobile.feature.login.data.api.LoginApiService
import com.ssafy.mobile.feature.login.data.dto.LoginRequestDto
import com.ssafy.mobile.feature.login.data.dto.LoginResponseDto
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class LoginRepository
    @Inject
    constructor(
        private val loginApiService: LoginApiService,
    ) {
        @Suppress("ThrowsCount")
        suspend fun login(
            email: String,
            password: String,
        ): LoginResponseDto =
            try {
                val response = loginApiService.login(LoginRequestDto(email, password))

                if (response.isSuccessful) {
                    response.body() ?: error("응답 본문이 비어 있습니다.")
                } else {
                    val errorMessage =
                        when (response.code()) {
                            HTTP_UNAUTHORIZED -> "이메일 또는 비밀번호가 올바르지 않습니다."
                            HTTP_BAD_REQUEST -> "입력 정보를 확인해 주세요."
                            else -> "로그인에 실패했습니다. (${response.code()})"
                        }
                    error(errorMessage)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw LoginException("네트워크 연결을 확인해 주세요.", e)
            }

        companion object {
            private const val HTTP_UNAUTHORIZED = 401
            private const val HTTP_BAD_REQUEST = 400
        }
    }

class LoginException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
