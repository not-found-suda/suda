package com.ssafy.mobile.feature.learning.data.repository

import android.util.Log
import com.ssafy.mobile.core.network.NetworkMonitor
import com.ssafy.mobile.feature.learning.data.api.LearningQuizApiService
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizAnswerRequestDto
import com.ssafy.mobile.feature.learning.data.dto.LearningQuizAnswerResponseDto
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizAnswerSubmissionSyncEvent
import com.ssafy.mobile.feature.learning.domain.model.PendingLearningQuizAnswerSubmission
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizAnswerSubmissionQueueRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

@Singleton
class LearningQuizAnswerSubmissionQueueSyncer
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
        private val queueRepository: LearningQuizAnswerSubmissionQueueRepository,
        private val apiService: LearningQuizApiService,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val syncMutex = Mutex()
        private val _events =
            MutableSharedFlow<LearningQuizAnswerSubmissionSyncEvent>(extraBufferCapacity = 16)
        val events: SharedFlow<LearningQuizAnswerSubmissionSyncEvent> = _events.asSharedFlow()
        private var isStarted = false

        fun start() {
            if (isStarted) return
            isStarted = true

            scope.launch {
                networkMonitor.isOnline
                    .filter { isOnline -> isOnline }
                    .collect {
                        syncPendingAnswerSubmissions()
                    }
            }
        }

        fun requestSync() {
            scope.launch {
                syncPendingAnswerSubmissions()
            }
        }

        suspend fun syncPendingAnswerSubmissions() {
            syncMutex.withLock {
                val submissions = queueRepository.getPendingAnswerSubmissions()
                for (submission in submissions) {
                    val shouldContinue = syncAnswerSubmission(submission)
                    if (!shouldContinue) break
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun syncAnswerSubmission(
            submission: PendingLearningQuizAnswerSubmission,
        ): Boolean =
            try {
                val response =
                    apiService.submitAnswer(
                        sessionId = submission.sessionId,
                        request =
                            LearningQuizAnswerRequestDto(
                                questionId = submission.questionId,
                                wordId = submission.wordId,
                                recognizedText = submission.recognizedText,
                            ),
                    )

                handleSyncResponse(
                    submission = submission,
                    response = response,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                markRetryFailed(submission, e.message)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resend pending quiz answer", e)
                markRetryFailed(submission, e.message)
                true
            }

        private suspend fun handleSyncResponse(
            submission: PendingLearningQuizAnswerSubmission,
            response: Response<LearningQuizAnswerResponseDto>,
        ): Boolean =
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    queueRepository.deleteAnswerSubmission(
                        sessionId = submission.sessionId,
                        questionId = submission.questionId,
                    )
                    if (body != null) {
                        _events.emit(
                            LearningQuizAnswerSubmissionSyncEvent.AnswerSynced(
                                result = body.toDomain(),
                            ),
                        )
                    } else {
                        emitAnswerAcceptedWithoutResult(submission)
                    }
                    true
                }
                response.code() == HTTP_STATUS_CONFLICT -> {
                    queueRepository.deleteAnswerSubmission(
                        sessionId = submission.sessionId,
                        questionId = submission.questionId,
                    )
                    emitAnswerAcceptedWithoutResult(submission)
                    true
                }
                response.code().isPermanentFailureStatusCode() -> {
                    queueRepository.deleteAnswerSubmission(
                        sessionId = submission.sessionId,
                        questionId = submission.questionId,
                    )
                    true
                }
                response.code().isAuthFailureStatusCode() -> {
                    markRetryFailed(
                        submission = submission,
                        failureMessage = "인증 후 다시 전송이 필요합니다.",
                    )
                    false
                }
                else -> {
                    markRetryFailed(
                        submission = submission,
                        failureMessage = "서버 응답 오류: ${response.code()}",
                    )
                    true
                }
            }

        private suspend fun emitAnswerAcceptedWithoutResult(
            submission: PendingLearningQuizAnswerSubmission,
        ) {
            _events.emit(
                LearningQuizAnswerSubmissionSyncEvent.AnswerAcceptedWithoutResult(
                    sessionId = submission.sessionId,
                    questionId = submission.questionId,
                ),
            )
        }

        private suspend fun markRetryFailed(
            submission: PendingLearningQuizAnswerSubmission,
            failureMessage: String?,
        ) {
            queueRepository.markAnswerSubmissionRetryFailed(
                sessionId = submission.sessionId,
                questionId = submission.questionId,
                failureMessage = failureMessage,
                failedAtMillis = System.currentTimeMillis(),
            )
        }

        private fun Int.isPermanentFailureStatusCode(): Boolean =
            this == HTTP_STATUS_BAD_REQUEST ||
                this == HTTP_STATUS_NOT_FOUND

        private fun Int.isAuthFailureStatusCode(): Boolean =
            this == HTTP_STATUS_UNAUTHORIZED || this == HTTP_STATUS_FORBIDDEN

        private companion object {
            const val TAG = "LearningAnswerQueue"
            const val HTTP_STATUS_BAD_REQUEST = 400
            const val HTTP_STATUS_UNAUTHORIZED = 401
            const val HTTP_STATUS_FORBIDDEN = 403
            const val HTTP_STATUS_NOT_FOUND = 404
            const val HTTP_STATUS_CONFLICT = 409
        }
    }
