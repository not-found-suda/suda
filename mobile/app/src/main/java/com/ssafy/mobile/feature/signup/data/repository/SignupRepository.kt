package com.ssafy.mobile.feature.signup.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
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
        @Suppress("ThrowsCount", "TooGenericExceptionCaught")
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
                    response.body() ?: throw SignupException("응답 본문이 비어 있습니다.")
                } else {
                    throw createSignupException(
                        status = response.code(),
                        errorBody = response.errorBody()?.string(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignupException) {
                throw e
            } catch (e: IOException) {
                throw SignupException("네트워크 연결을 확인해 주세요.", e)
            } catch (e: Exception) {
                throw SignupException("알 수 없는 오류가 발생했습니다.", e)
            }

        private fun createSignupException(
            status: Int,
            errorBody: String?,
        ): SignupException {
            val problemDetails = parseProblemDetails(errorBody)
            val defaultMessage =
                when (status) {
                    HTTP_CONFLICT -> "이미 사용 중인 이메일입니다."
                    HTTP_BAD_REQUEST -> "입력 정보를 확인해 주세요."
                    else -> "회원가입에 실패했습니다. ($status)"
                }

            Log.e(
                TAG,
                "Signup failed. " +
                    "status=$status, " +
                    "code=${problemDetails?.code}, " +
                    "traceId=${problemDetails?.traceId}, " +
                    "instance=${problemDetails?.instance}",
            )

            return SignupException(
                message = defaultMessage,
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
                Log.e(TAG, "Failed to parse signup error body", e)
                null
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to parse signup error body", e)
                null
            }
        }

        companion object {
            private const val TAG = "SignupRepository"
            private const val HTTP_BAD_REQUEST = 400
            private const val HTTP_CONFLICT = 409
            private val gson = Gson()
        }
    }

class SignupException(
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
