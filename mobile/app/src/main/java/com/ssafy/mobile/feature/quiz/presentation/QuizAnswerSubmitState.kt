package com.ssafy.mobile.feature.quiz.presentation

sealed interface QuizAnswerSubmitState {
    data object Idle : QuizAnswerSubmitState

    data object Submitting : QuizAnswerSubmitState

    data class TimedOut(
        val message: String,
    ) : QuizAnswerSubmitState

    data object Success : QuizAnswerSubmitState

    data class CompletionPending(
        val message: String,
    ) : QuizAnswerSubmitState

    data class SaveFailed(
        val message: String,
    ) : QuizAnswerSubmitState

    data class Error(
        val message: String,
    ) : QuizAnswerSubmitState
}
