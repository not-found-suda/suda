package com.ssafy.mobile.feature.quiz.domain.model

object QuizRetryPolicy {
    fun hasSuccessfulAnswer(answer: QuizAnswer?): Boolean = (answer?.star ?: 0) >= PASSING_STAR

    fun remainingRetryCount(answer: QuizAnswer?): Int =
        if (answer == null || hasSuccessfulAnswer(answer)) {
            MAX_ANSWER_ATTEMPTS - 1
        } else {
            (MAX_ANSWER_ATTEMPTS - answer.attemptCount).coerceAtLeast(0)
        }

    fun isRetryLimitReached(answer: QuizAnswer?): Boolean =
        answer != null &&
            !hasSuccessfulAnswer(answer) &&
            remainingRetryCount(answer) == 0

    fun canMoveNext(
        answer: QuizAnswer?,
        canSkipQuestion: Boolean,
    ): Boolean = hasSuccessfulAnswer(answer) || isRetryLimitReached(answer) || canSkipQuestion

    private const val PASSING_STAR = 3
    private const val MAX_ANSWER_ATTEMPTS = 3
}
