package com.ssafy.mobile.feature.conversation.data.repository

import android.util.Log
import com.google.gson.Gson
import com.ssafy.mobile.feature.conversation.data.remote.CommsSessionApiService
import com.ssafy.mobile.feature.conversation.data.remote.model.ApiErrorResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.CommsSessionCreateRequest
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.Response

class CommsSessionRepository
    @Inject
    constructor(
        private val apiService: CommsSessionApiService,
    ) {
        suspend fun createSession(childProfileId: Long): Result<Long> =
            try {
                val response =
                    apiService.createSession(
                        request = CommsSessionCreateRequest(childProfileId = childProfileId),
                    )

                if (response.isSuccessful) {
                    val body =
                        response.body()
                            ?: return Result.failure(IllegalStateException(ERROR_EMPTY_BODY))
                    Result.success(body.sessionId)
                } else {
                    Result.failure(response.toApiException())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Comms session create network error", e)
                Result.failure(IOException("대화 기록 세션을 만들지 못했습니다."))
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e(TAG, "Comms session create unknown error", e)
                Result.failure(IllegalStateException("대화 기록 세션을 만드는 중 오류가 발생했습니다."))
            }

        suspend fun endSession(sessionId: Long): Result<Unit> =
            try {
                val response = apiService.endSession(sessionId = sessionId)

                if (response.isSuccessful || response.code() == HTTP_CONFLICT) {
                    Result.success(Unit)
                } else {
                    Result.failure(response.toApiException())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Comms session end network error", e)
                Result.failure(IOException("대화 기록 세션을 종료하지 못했습니다."))
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e(TAG, "Comms session end unknown error", e)
                Result.failure(IllegalStateException("대화 기록 세션을 종료하는 중 오류가 발생했습니다."))
            }

        private fun Response<*>.toApiException(): IOException {
            val errorBody = errorBody()?.string().orEmpty()
            val problemDetails = errorBody.parseProblemDetails()
            val diagnostic =
                problemDetails
                    ?.toDiagnosticMessage()
                    ?.takeIf { it.isNotBlank() }
                    ?: "body=${errorBody.take(ERROR_BODY_PREVIEW_LENGTH)}"

            return IOException(
                "$ERROR_API_PREFIX status=${code()}, message=${message()}, $diagnostic",
            )
        }

        private fun String.parseProblemDetails(): ApiErrorResponse? {
            if (isBlank()) return null

            return runCatching {
                gson.fromJson(this, ApiErrorResponse::class.java)
            }.getOrNull()
        }

        private fun ApiErrorResponse.toDiagnosticMessage(): String =
            listOfNotNull(
                type?.let { "type=$it" },
                title?.let { "title=$it" },
                status?.let { "status=$it" },
                detail?.let { "detail=$it" },
                instance?.let { "instance=$it" },
                code?.let { "code=$it" },
                traceId?.let { "traceId=$it" },
            ).joinToString()

        private companion object {
            const val TAG = "CommsSessionRepository"
            const val ERROR_EMPTY_BODY = "Empty response body"
            const val ERROR_API_PREFIX = "Comms Session API Error:"
            const val ERROR_BODY_PREVIEW_LENGTH = 300
            const val HTTP_CONFLICT = 409
            val gson = Gson()
        }
    }
