package com.ssafy.mobile.feature.learning.data.repository

import android.util.Log
import com.ssafy.mobile.feature.learning.data.api.LearningQuizApiService
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizSessionRequestDto
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizAnswerResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizQuestion
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizSession
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizSessionStatus
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

private const val QUIZ_TAG = "RemoteLearningQuizRepo"
private const val COMPLETED_STATUS = "COMPLETED"
private const val HTTP_STATUS_BAD_REQUEST = 400
private const val HTTP_STATUS_UNAUTHORIZED = 401
private const val HTTP_STATUS_FORBIDDEN = 403
private const val HTTP_STATUS_NOT_FOUND = 404
private const val HTTP_STATUS_CONFLICT = 409
private const val HTTP_STATUS_INTERNAL_SERVER_ERROR = 500
private const val COMPLETED_ENDED_AT_FALLBACK = ""

class RemoteLearningQuizRepository
    @Inject
    constructor(
        private val apiService: LearningQuizApiService,
    ) : LearningQuizRepository {
        override suspend fun createSession(
            childProfileId: Long,
            categoryId: Long,
            difficulty: String,
            totalQuestionCount: Int,
        ): Result<LearningQuizSession> =
            runCatchingNetwork("Failed to create quiz session") {
                apiService
                    .createSession(
                        LearningQuizSessionRequestDto(
                            childProfileId = childProfileId,
                            categoryId = categoryId,
                            difficulty = difficulty,
                            totalQuestionCount = totalQuestionCount,
                        ),
                    ).toResult { response ->
                        response.toDomain()
                    }
            }

        override suspend fun getCurrentQuestion(sessionId: Long): Result<LearningQuizQuestion> =
            runCatchingNetwork("Failed to load current quiz question") {
                apiService.getCurrentQuestion(sessionId).toResult { response ->
                    response.toDomain()
                }
            }

        override suspend fun submitAnswer(
            sessionId: Long,
            questionId: Long,
            audioFile: File?,
            audioMimeType: String?,
        ): Result<LearningQuizAnswerResult> =
            when {
                audioFile == null && audioMimeType == null ->
                    runCatchingNetwork("Failed to submit quiz answer") {
                        apiService
                            .submitAnswer(
                                sessionId = sessionId,
                                questionId = questionId,
                                audioFile = null,
                                recognizedText =
                                    EMPTY_RECOGNIZED_TEXT.toRequestBody(
                                        MULTIPART_TEXT_MEDIA_TYPE,
                                    ),
                            ).toResult { response ->
                                response.toDomain(
                                    fallbackSessionId = sessionId,
                                    fallbackQuestionId = questionId,
                                )
                            }
                    }

                audioFile == null ||
                    audioMimeType == null ||
                    !audioFile.isUsableAudioFile() ->
                    Result.failure(IllegalStateException("녹음 파일이 비어 있습니다. 다시 말해 주세요."))

                else ->
                    runCatchingNetwork("Failed to submit quiz answer") {
                        apiService
                            .submitAnswer(
                                sessionId = sessionId,
                                questionId = questionId,
                                audioFile = audioFile.toMultipartAudioPart(audioMimeType),
                            ).toResult { response ->
                                response.toDomain(
                                    fallbackSessionId = sessionId,
                                    fallbackQuestionId = questionId,
                                )
                            }
                    }
            }

        override suspend fun completeSession(sessionId: Long): Result<LearningQuizSessionStatus> =
            runCatchingNetwork("Failed to complete quiz session") {
                val response =
                    apiService
                        .updateSessionStatus(
                            sessionId = sessionId,
                        )

                if (response.code() == HTTP_STATUS_CONFLICT) {
                    Result.success(
                        LearningQuizSessionStatus(
                            sessionId = sessionId,
                            status = COMPLETED_STATUS,
                            endedAt = COMPLETED_ENDED_AT_FALLBACK,
                        ),
                    )
                } else {
                    response.toResult { response ->
                        response.toDomain()
                    }
                }
            }

        override suspend fun getResult(sessionId: Long): Result<LearningQuizResult> =
            runCatchingNetwork("Failed to load quiz result") {
                apiService.getResult(sessionId).toResult { response ->
                    response.toDomain()
                }
            }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T> runCatchingNetwork(
            logMessage: String,
            block: suspend () -> Result<T>,
        ): Result<T> =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(QUIZ_TAG, logMessage, e)
                Result.failure(IOException("네트워크 연결을 확인해 주세요."))
            } catch (e: Exception) {
                Log.e(QUIZ_TAG, logMessage, e)
                Result.failure(IllegalStateException("퀴즈 요청 처리 중 오류가 발생했습니다."))
            }

        private fun <Dto, Domain> Response<Dto>.toResult(mapper: (Dto) -> Domain): Result<Domain> =
            if (isSuccessful) {
                body()?.let { responseBody ->
                    Result.success(mapper(responseBody))
                } ?: Result.failure(
                    IllegalStateException("서버 응답이 비어 있습니다."),
                )
            } else {
                Result.failure(IllegalStateException(errorMessage(code())))
            }

        private fun errorMessage(statusCode: Int): String =
            when (statusCode) {
                HTTP_STATUS_BAD_REQUEST -> "퀴즈 요청 값이 올바르지 않습니다."
                HTTP_STATUS_UNAUTHORIZED -> "로그인이 필요합니다."
                HTTP_STATUS_FORBIDDEN -> "퀴즈 요청 권한이 없습니다."
                HTTP_STATUS_NOT_FOUND -> "퀴즈 세션 또는 문제를 찾을 수 없습니다."
                HTTP_STATUS_CONFLICT -> "현재 퀴즈 상태에서는 요청을 처리할 수 없습니다."
                HTTP_STATUS_INTERNAL_SERVER_ERROR -> "서버에서 퀴즈 요청 처리에 실패했습니다."
                else -> "퀴즈 요청 처리 중 오류가 발생했습니다."
            }

        private fun File.toMultipartAudioPart(audioMimeType: String): MultipartBody.Part =
            MultipartBody.Part.createFormData(
                name = AUDIO_FILE_PART_NAME,
                filename = name,
                body = asRequestBody(audioMimeType.toMediaTypeOrNull()),
            )

        private fun File.isUsableAudioFile(): Boolean =
            exists() && length() > MIN_VALID_WAV_FILE_BYTES
    }

private const val AUDIO_FILE_PART_NAME = "audioFile"
private const val MIN_VALID_WAV_FILE_BYTES = 44L
private const val EMPTY_RECOGNIZED_TEXT = ""
private val MULTIPART_TEXT_MEDIA_TYPE = "text/plain".toMediaTypeOrNull()
