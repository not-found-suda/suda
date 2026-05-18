package com.ssafy.mobile.feature.quiz.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionReducerTest {
    @Test
    fun startCreatesSessionWithMockFiveQuestions() {
        val state = QuizSessionReducer.start(MockQuizQuestions.items)

        assertEquals(MockQuizQuestions.QUESTION_COUNT, state.totalQuestionCount)
        assertEquals(FIRST_QUESTION_INDEX, state.currentQuestionIndex)
        assertEquals(FIRST_QUESTION_NUMBER, state.currentQuestionNumber)
        assertEquals(FIRST_PROGRESS, state.progress, FLOAT_DELTA)
        assertFalse(state.isFinished)
        assertEquals("밥", state.currentQuestion?.word)
    }

    @Test
    fun moveToNextQuestionIncrementsIndexAndProgress() {
        val state = QuizSessionReducer.start(MockQuizQuestions.items)

        val nextState = QuizSessionReducer.moveToNextQuestion(state)

        assertEquals(SECOND_QUESTION_INDEX, nextState.currentQuestionIndex)
        assertEquals(SECOND_QUESTION_NUMBER, nextState.currentQuestionNumber)
        assertEquals(SECOND_PROGRESS, nextState.progress, FLOAT_DELTA)
        assertFalse(nextState.isFinished)
        assertEquals("물", nextState.currentQuestion?.word)
    }

    @Test
    fun moveToNextQuestionFinishesSessionAfterLastQuestion() {
        val lastQuestionState =
            repeatMoveToNextQuestion(
                initialState = QuizSessionReducer.start(MockQuizQuestions.items),
                repeatCount = MockQuizQuestions.QUESTION_COUNT - 1,
            )

        assertTrue(lastQuestionState.isLastQuestion)

        val finishedState = QuizSessionReducer.moveToNextQuestion(lastQuestionState)

        assertTrue(finishedState.isFinished)
        assertEquals(MockQuizQuestions.QUESTION_COUNT, finishedState.currentQuestionNumber)
        assertEquals(FINISHED_PROGRESS, finishedState.progress, FLOAT_DELTA)
        assertNull(finishedState.currentQuestion)
    }

    @Test
    fun retryCurrentQuestionKeepsQuestionIndexAndIncrementsRetryCount() {
        val state = QuizSessionReducer.start(MockQuizQuestions.items)

        val retryState = QuizSessionReducer.retryCurrentQuestion(state)

        assertEquals(state.currentQuestionIndex, retryState.currentQuestionIndex)
        assertEquals(state.currentQuestion?.id, retryState.currentQuestion?.id)
        assertEquals(ONE_RETRY, retryState.retryCount)
        assertFalse(retryState.isFinished)
    }

    @Test
    fun moveToNextQuestionResetsRetryCount() {
        val retryState =
            QuizSessionReducer.retryCurrentQuestion(
                QuizSessionReducer.start(MockQuizQuestions.items),
            )

        val nextState = QuizSessionReducer.moveToNextQuestion(retryState)

        assertEquals(SECOND_QUESTION_INDEX, nextState.currentQuestionIndex)
        assertEquals(NO_RETRY, nextState.retryCount)
    }

    @Test
    fun submitCurrentAnswerStoresLatestAnswerForCurrentQuestion() {
        val firstAnsweredState =
            QuizSessionReducer.submitCurrentAnswer(
                state = QuizSessionReducer.start(MockQuizQuestions.items),
                sttText = "물",
                star = ONE_STAR,
            )

        val answeredState =
            QuizSessionReducer.submitCurrentAnswer(
                state = firstAnsweredState,
                sttText = "밥",
                star = THREE_STARS,
            )

        assertEquals(1, answeredState.answers.size)
        assertEquals(MockQuizQuestions.items.first().id, answeredState.answers.first().questionId)
        assertEquals("밥", answeredState.answers.first().sttText)
        assertEquals(THREE_STARS, answeredState.answers.first().star)
        assertEquals(TWO_ATTEMPTS, answeredState.answers.first().attemptCount)
    }

    @Test
    fun submitCurrentAnswerStoresFirstSubmissionAsOneAttempt() {
        val state = QuizSessionReducer.start(MockQuizQuestions.items)

        val answeredState =
            QuizSessionReducer.submitCurrentAnswer(
                state = state,
                sttText = "밥",
                star = THREE_STARS,
            )

        assertEquals(ONE_ATTEMPT, answeredState.answers.first().attemptCount)
    }

    @Test
    fun submitCurrentAnswerReplacesPreviousAnswerForSameQuestion() {
        val state = QuizSessionReducer.start(MockQuizQuestions.items)
        val firstAnsweredState =
            QuizSessionReducer.submitCurrentAnswer(
                state = state,
                sttText = "물",
                star = ONE_STAR,
            )

        val secondAnsweredState =
            QuizSessionReducer.submitCurrentAnswer(
                state = firstAnsweredState,
                sttText = "밥",
                star = THREE_STARS,
            )

        assertEquals(1, secondAnsweredState.answers.size)
        assertEquals("밥", secondAnsweredState.answers.first().sttText)
        assertEquals(THREE_STARS, secondAnsweredState.answers.first().star)
        assertEquals(TWO_ATTEMPTS, secondAnsweredState.answers.first().attemptCount)
    }

    @Test
    fun submitCurrentAnswerUsesPreviousAnswerAttemptCountEvenWhenRetryCountIsReset() {
        val firstAnsweredState =
            QuizSessionReducer.submitCurrentAnswer(
                state = QuizSessionReducer.start(MockQuizQuestions.items),
                sttText = "물",
                star = ONE_STAR,
            )
        val stateWithResetRetryCount = firstAnsweredState.copy(retryCount = NO_RETRY)

        val secondAnsweredState =
            QuizSessionReducer.submitCurrentAnswer(
                state = stateWithResetRetryCount,
                sttText = "밥",
                star = THREE_STARS,
            )

        assertEquals(TWO_ATTEMPTS, secondAnsweredState.answers.first().attemptCount)
    }

    @Test
    fun resetReturnsEmptyIdleState() {
        val state = QuizSessionReducer.reset()

        assertEquals(0, state.totalQuestionCount)
        assertEquals(0, state.currentQuestionIndex)
        assertEquals(0, state.currentQuestionNumber)
        assertEquals(0f, state.progress, FLOAT_DELTA)
        assertFalse(state.isFinished)
        assertNull(state.currentQuestion)
    }

    private fun repeatMoveToNextQuestion(
        initialState: QuizSessionState,
        repeatCount: Int,
    ): QuizSessionState {
        var state = initialState
        repeat(repeatCount) {
            state = QuizSessionReducer.moveToNextQuestion(state)
        }
        return state
    }

    private companion object {
        const val FIRST_QUESTION_INDEX = 0
        const val SECOND_QUESTION_INDEX = 1
        const val FIRST_QUESTION_NUMBER = 1
        const val SECOND_QUESTION_NUMBER = 2
        const val NO_RETRY = 0
        const val ONE_RETRY = 1
        const val ONE_ATTEMPT = 1
        const val TWO_ATTEMPTS = 2
        const val ONE_STAR = 1
        const val THREE_STARS = 3
        const val FIRST_PROGRESS = 0.2f
        const val SECOND_PROGRESS = 0.4f
        const val FINISHED_PROGRESS = 1f
        const val FLOAT_DELTA = 0.0001f
    }
}
