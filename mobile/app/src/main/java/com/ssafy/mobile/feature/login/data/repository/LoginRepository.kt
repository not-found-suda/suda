package com.ssafy.mobile.feature.login.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.feature.login.data.api.LoginApiService
import com.ssafy.mobile.feature.login.data.dto.LoginRequestDto
import com.ssafy.mobile.feature.login.data.dto.LoginResponseDto
import com.ssafy.mobile.feature.login.data.dto.NaverOAuthLoginRequestDto
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class LoginRepository
    @Inject
    constructor(
        private val loginApiService: LoginApiService,
    ) {
        @Suppress("ThrowsCount", "TooGenericExceptionCaught")
        suspend fun login(
            email: String,
            password: String,
        ): LoginResponseDto =
            try {
                val response = loginApiService.login(LoginRequestDto(email, password))

                if (response.isSuccessful) {
                    response.body() ?: throw LoginException("응답 본문이 비어 있습니다.")
                } else {
                    val errorMessage =
                        when (response.code()) {
                            HTTP_UNAUTHORIZED -> "이메일 또는 비밀번호가 올바르지 않습니다."
                            HTTP_BAD_REQUEST -> "입력 정보를 확인해 주세요."
                            else -> "로그인에 실패했습니다. (${response.code()})"
                        }
                    throw LoginException(errorMessage)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginException) {
                throw e
            } catch (e: IOException) {
                throw LoginException("네트워크 연결을 확인해 주세요.", e)
            } catch (e: Exception) {
                throw LoginException("알 수 없는 오류가 발생했습니다.", e)
            }

        @Suppress("ThrowsCount", "TooGenericExceptionCaught")
        suspend fun loginWithNaver(providerAccessToken: String): LoginResponseDto =
            try {
                val response =
                    loginApiService.loginWithNaver(
                        NaverOAuthLoginRequestDto(
                            providerAccessToken = providerAccessToken,
                        ),
                    )

                if (response.isSuccessful) {
                    response.body() ?: throw LoginException("응답 본문이 비어 있습니다.")
                } else {
                    throw createNaverLoginException(
                        status = response.code(),
                        errorBody = response.errorBody()?.string(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginException) {
                throw e
            } catch (e: IOException) {
                throw LoginException("네트워크 연결을 확인해 주세요.", e)
            } catch (e: Exception) {
                throw LoginException("알 수 없는 오류가 발생했습니다.", e)
            }

        private fun createNaverLoginException(
            status: Int,
            errorBody: String?,
        ): LoginException {
            val problemDetails = parseProblemDetails(errorBody)
            val defaultMessage =
                when (status) {
                    HTTP_UNAUTHORIZED -> "네이버 인증이 올바르지 않습니다."
                    HTTP_BAD_REQUEST -> "인증 정보를 확인해 주세요."
                    else -> "네이버 로그인에 실패했습니다. ($status)"
                }

            Log.e(
                TAG,
                "Naver login failed. " +
                    "status=$status, " +
                    "code=${problemDetails?.code}, " +
                    "traceId=${problemDetails?.traceId}, " +
                    "instance=${problemDetails?.instance}",
            )

            val message =
                problemDetails?.detail?.takeIf { it.isNotBlank() }
                    ?: defaultMessage

            return LoginException(
                message = message,
                status = problemDetails?.status ?: status,
                code = problemDetails?.code,
                traceId = problemDetails?.traceId,
                instance = problemDetails?.instance,
            )
        }

        private fun parseProblemDetails(errorBody: String?): ProblemDetailsDto? {
            if (errorBody.isNullOrBlank()) return null

            return try {
                gson.fromJson(errorBody, ProblemDetailsDto::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Failed to parse naver login error body", e)
                null
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to parse naver login error body", e)
                null
            }
        }

        companion object {
            private const val TAG = "LoginRepository"
            private const val HTTP_UNAUTHORIZED = 401
            private const val HTTP_BAD_REQUEST = 400
            private val gson = Gson()
        }
    }

class LoginException(
    message: String,
    cause: Throwable? = null,
    val status: Int? = null,
    val code: String? = null,
    val traceId: String? = null,
    val instance: String? = null,
) : RuntimeException(message, cause)

private data class ProblemDetailsDto(
    @SerializedName("status")
    val status: Int?,
    @SerializedName("detail")
    val detail: String?,
    @SerializedName("code")
    val code: String?,
    @SerializedName("traceId")
    val traceId: String?,
    @SerializedName("instance")
    val instance: String?,
)
