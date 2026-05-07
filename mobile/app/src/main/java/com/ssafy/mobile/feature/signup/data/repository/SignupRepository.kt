package com.ssafy.mobile.feature.signup.data.repository

import com.ssafy.mobile.feature.signup.data.api.SignupApiService
import com.ssafy.mobile.feature.signup.data.dto.SignupRequestDto
import com.ssafy.mobile.feature.signup.data.dto.SignupResponseDto
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class SignupRepository
    @Inject
    constructor(
        private val signupApiService: SignupApiService,
    ) {
        @Suppress("ThrowsCount")
        suspend fun signup(
            email: String,
            password: String,
            name: String,
        ): SignupResponseDto =
            try {
                val response =
                    signupApiService.signup(
                        SignupRequestDto(
                            email = email,
                            password = password,
                            name = name,
                        ),
                    )

                if (response.isSuccessful) {
                    response.body() ?: error("응답 본문이 비어 있습니다.")
                } else {
                    val errorMessage =
                        when (response.code()) {
                            HTTP_CONFLICT -> "이미 사용 중인 이메일입니다."
                            HTTP_BAD_REQUEST -> "입력 정보를 확인해 주세요."
                            else -> "회원가입에 실패했습니다. (${response.code()})"
                        }
                    error(errorMessage)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw SignupException("네트워크 연결을 확인해 주세요.", e)
            }

        companion object {
            private const val HTTP_BAD_REQUEST = 400
            private const val HTTP_CONFLICT = 409
        }
    }

class SignupException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
