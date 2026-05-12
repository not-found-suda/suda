package com.ssafy.mobile.feature.quiz.domain.model

object QuizRetryPolicy {
    fun hasSuccessfulAnswer(answer: QuizAnswer?): Boolean = answer?.isCorrect == true

    fun remainingRetryCount(answer: QuizAnswer?): Int =
        if (answer == null) {
            MAX_ANSWER_ATTEMPTS - 1
        } else {
            (MAX_ANSWER_ATTEMPTS - answer.attemptCount).coerceAtLeast(0)
        }

    fun isRetryLimitReached(answer: QuizAnswer?): Boolean =
        answer?.isScored == true &&
            !hasSuccessfulAnswer(answer) &&
            remainingRetryCount(answer) == 0

    fun canMoveNext(
        answer: QuizAnswer?,
        canSkipQuestion: Boolean,
    ): Boolean =
        canSkipQuestion ||
            (
                answer?.isScored == true &&
                    (hasSuccessfulAnswer(answer) || isRetryLimitReached(answer))
            )

    private const val MAX_ANSWER_ATTEMPTS = 3
}
