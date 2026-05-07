package com.ssafy.mobile.feature.quiz.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizRetryPolicyTest {
    @Test
    fun lowStarAnswerCanRetryBeforeLimit() {
        val answer = answer(star = ONE_STAR, attemptCount = ONE_ATTEMPT)

        assertEquals(TWO_RETRIES_LEFT, QuizRetryPolicy.remainingRetryCount(answer))
        assertFalse(QuizRetryPolicy.isRetryLimitReached(answer))
        assertFalse(QuizRetryPolicy.canMoveNext(answer, canSkipQuestion = false))
    }

    @Test
    fun lowStarAnswerCanMoveNextAfterRetryLimit() {
        val answer = answer(star = TWO_STARS, attemptCount = MAX_ATTEMPTS)

        assertEquals(NO_RETRY_LEFT, QuizRetryPolicy.remainingRetryCount(answer))
        assertTrue(QuizRetryPolicy.isRetryLimitReached(answer))
        assertTrue(QuizRetryPolicy.canMoveNext(answer, canSkipQuestion = false))
    }

    @Test
    fun threeStarAnswerCanMoveNextImmediately() {
        val answer = answer(star = THREE_STARS, attemptCount = ONE_ATTEMPT)

        assertTrue(QuizRetryPolicy.hasSuccessfulAnswer(answer))
        assertTrue(QuizRetryPolicy.canMoveNext(answer, canSkipQuestion = false))
    }

    @Test
    fun skipStateCanMoveNextWithoutAnswer() {
        assertTrue(QuizRetryPolicy.canMoveNext(answer = null, canSkipQuestion = true))
    }

    private fun answer(
        star: Int,
        attemptCount: Int,
    ): QuizAnswer =
        QuizAnswer(
            questionId = QUESTION_ID,
            sttText = "테스트",
            star = star,
            attemptCount = attemptCount,
        )

    private companion object {
        const val QUESTION_ID = 1L
        const val NO_RETRY_LEFT = 0
        const val ONE_ATTEMPT = 1
        const val TWO_RETRIES_LEFT = 2
        const val MAX_ATTEMPTS = 3
        const val ONE_STAR = 1
        const val TWO_STARS = 2
        const val THREE_STARS = 3
    }
}
