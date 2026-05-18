package com.ssafy.mobile.feature.learning.data.repository

import android.util.Log
import com.ssafy.mobile.core.network.NetworkMonitor
import com.ssafy.mobile.feature.learning.domain.model.PendingLearningQuizAnswerSubmission
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizAnswerSubmissionQueueRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LearningQuizAnswerSubmissionQueueSyncer
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
        private val queueRepository: LearningQuizAnswerSubmissionQueueRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val syncMutex = Mutex()
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
                Log.i(
                    TAG,
                    "Dropping legacy text quiz answer submission: " +
                        "sessionId=${submission.sessionId}, questionId=${submission.questionId}",
                )
                queueRepository.deleteAnswerSubmission(
                    sessionId = submission.sessionId,
                    questionId = submission.questionId,
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to drop legacy pending quiz answer", e)
                markRetryFailed(submission, e.message)
                true
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

        private companion object {
            const val TAG = "LearningAnswerQueue"
        }
    }
