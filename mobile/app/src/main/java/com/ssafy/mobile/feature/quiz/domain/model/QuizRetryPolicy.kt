package com.ssafy.mobile.feature.quiz.domain.model

object QuizRetryPolicy {
    fun hasSuccessfulAnswer(answer: QuizAnswer?): Boolean = answer?.isCorrect == true

    fun remainingRetryCount(answer: QuizAnswer?): Int =
        when {
            answer == null -> MAX_ANSWER_ATTEMPTS - 1
            answer.isScored -> 0
            else -> (MAX_ANSWER_ATTEMPTS - answer.attemptCount).coerceAtLeast(0)
        }

    fun isRetryLimitReached(answer: QuizAnswer?): Boolean =
        answer?.isScored == true &&
            !hasSuccessfulAnswer(answer)

    fun canMoveNext(
        answer: QuizAnswer?,
        canSkipQuestion: Boolean,
    ): Boolean =
        canSkipQuestion ||
            answer?.isScored == true

    private const val MAX_ANSWER_ATTEMPTS = 3
}
