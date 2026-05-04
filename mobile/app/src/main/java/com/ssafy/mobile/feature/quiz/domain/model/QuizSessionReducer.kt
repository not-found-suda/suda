package com.ssafy.mobile.feature.quiz.domain.model

object QuizSessionReducer {
    fun start(questions: List<QuizQuestion>): QuizSessionState =
        QuizSessionState(
            questions = questions,
            isFinished = questions.isEmpty(),
        )

    fun moveToNextQuestion(state: QuizSessionState): QuizSessionState {
        if (state.isFinished || state.questions.isEmpty()) {
            return state.copy(isFinished = true)
        }

        return if (state.isLastQuestion) {
            state.copy(isFinished = true)
        } else {
            state.copy(
                currentQuestionIndex = state.currentQuestionIndex + 1,
                retryCount = 0,
            )
        }
    }

    fun retryCurrentQuestion(state: QuizSessionState): QuizSessionState {
        if (state.isFinished) return state

        return state.copy(retryCount = state.retryCount + 1)
    }

    fun submitCurrentAnswer(
        state: QuizSessionState,
        sttText: String,
        star: Int,
    ): QuizSessionState {
        val question = state.currentQuestion ?: return state
        val answer =
            QuizAnswer(
                questionId = question.id,
                sttText = sttText,
                star = star,
                attemptCount = state.retryCount + 1,
            )

        return state.copy(
            answers = state.answers.replaceAnswer(answer),
        )
    }

    fun reset(): QuizSessionState = QuizSessionState()

    private fun List<QuizAnswer>.replaceAnswer(answer: QuizAnswer): List<QuizAnswer> =
        filterNot { it.questionId == answer.questionId } + answer
}
