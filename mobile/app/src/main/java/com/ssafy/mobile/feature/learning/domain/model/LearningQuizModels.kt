package com.ssafy.mobile.feature.learning.domain.model

const val DEFAULT_QUIZ_QUESTION_COUNT = 5

data class LearningQuizSession(
    val sessionId: Long,
    val categoryId: Long,
    val difficulty: String,
    val totalQuestionCount: Int,
    val currentQuestionNumber: Int,
    val status: String,
    val imageUrls: List<String> = emptyList(),
    val questions: List<LearningQuizSessionQuestion> = emptyList(),
)

data class LearningQuizSessionQuestion(
    val questionId: Long,
    val questionNumber: Int,
    val targetText: String,
    val wordId: Long? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
)

data class LearningQuizQuestion(
    val sessionId: Long,
    val questionId: Long,
    val wordId: Long,
    val questionNumber: Int,
    val totalQuestionCount: Int,
    val imageUrl: String,
    val audioUrl: String? = null,
    val targetText: String? = null,
)

data class LearningQuizAnswerResult(
    val sessionId: Long,
    val questionId: Long,
    val wordId: Long,
    val targetText: String,
    val recognizedText: String,
    val isCorrect: Boolean,
    val star: Int,
    val feedback: String? = null,
    val hasNext: Boolean,
    val nextQuestionNumber: Int? = null,
)

data class LearningQuizSessionStatus(
    val sessionId: Long,
    val status: String,
    val endedAt: String,
)

data class LearningQuizResult(
    val sessionId: Long,
    val totalQuestionCount: Int,
    val correctCount: Int,
    val totalStar: Int,
    val answers: List<LearningQuizResultAnswer>,
)

data class LearningQuizResultAnswer(
    val questionId: Long,
    val wordId: Long,
    val targetText: String,
    val recognizedText: String? = null,
    val isCorrect: Boolean,
    val star: Int,
    val feedback: String? = null,
)
