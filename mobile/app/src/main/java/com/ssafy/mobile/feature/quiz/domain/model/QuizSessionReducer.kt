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
        val previousAnswer =
            state.answers.firstOrNull {
                it.questionId == question.id
            }
        val answer =
            QuizAnswer(
                questionId = question.id,
                sttText = sttText,
                star = star,
                attemptCount = (previousAnswer?.attemptCount ?: 0) + 1,
            )

        return state.copy(
            answers = state.answers.replaceAnswer(answer),
            retryCount = (answer.attemptCount - 1).coerceAtLeast(0),
        )
    }

    fun reset(): QuizSessionState = QuizSessionState()

    private fun List<QuizAnswer>.replaceAnswer(answer: QuizAnswer): List<QuizAnswer> =
        filterNot { it.questionId == answer.questionId } + answer
}
