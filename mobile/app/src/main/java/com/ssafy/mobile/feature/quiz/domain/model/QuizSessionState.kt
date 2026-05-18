package com.ssafy.mobile.feature.quiz.domain.model

data class QuizSessionState(
    val questions: List<QuizQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val answers: List<QuizAnswer> = emptyList(),
    val retryCount: Int = 0,
    val isFinished: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sessionId: Long? = null,
    val totalQuestionCountOverride: Int? = null,
    val currentQuestionNumberOverride: Int? = null,
    val preloadImageUrls: List<String> = emptyList(),
) {
    val totalQuestionCount: Int
        get() = totalQuestionCountOverride ?: questions.size

    val currentQuestionNumber: Int
        get() =
            currentQuestionNumberOverride
                ?: when {
                    questions.isEmpty() -> 0
                    isFinished -> totalQuestionCount
                    else -> currentQuestionIndex + 1
                }

    val currentQuestion: QuizQuestion?
        get() = questions.getOrNull(currentQuestionIndex).takeUnless { isFinished }

    val progress: Float
        get() =
            if (questions.isEmpty()) {
                EMPTY_PROGRESS
            } else {
                currentQuestionNumber.toFloat() / totalQuestionCount
            }

    val isLastQuestion: Boolean
        get() = questions.isNotEmpty() && currentQuestionNumber == totalQuestionCount

    companion object {
        private const val EMPTY_PROGRESS = 0f
    }
}
