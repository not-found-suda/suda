package com.ssafy.mobile.feature.conversation.presentation

sealed interface TranslationFeedbackSubmitState {
    data object Idle : TranslationFeedbackSubmitState

    data object Submitting : TranslationFeedbackSubmitState

    data object Success : TranslationFeedbackSubmitState

    data class Error(
        val message: String,
    ) : TranslationFeedbackSubmitState
}
