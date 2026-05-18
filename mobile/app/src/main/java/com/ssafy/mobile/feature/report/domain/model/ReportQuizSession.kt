package com.ssafy.mobile.feature.report.domain.model

data class ReportQuizSessionPage(
    val sessions: List<ReportQuizSession>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    val hasSessions: Boolean
        get() = sessions.isNotEmpty()
}

data class ReportQuizSession(
    val sessionId: Long,
    val categoryId: Long,
    val categoryName: String,
    val difficulty: String,
    val totalQuestionCount: Int,
    val correctCount: Int,
    val accuracyRate: Double,
    val averageStar: Double,
    val status: String,
    val startedAt: String?,
    val endedAt: String?,
)

data class ReportQuizSessionDetail(
    val sessionId: Long,
    val childId: Long,
    val categoryId: Long,
    val categoryName: String,
    val difficulty: String,
    val totalQuestionCount: Int,
    val correctCount: Int,
    val accuracyRate: Double,
    val totalStar: Int?,
    val averageStar: Double,
    val status: String,
    val startedAt: String?,
    val endedAt: String?,
    val answers: List<ReportQuizAnswer>,
)

data class ReportQuizAnswer(
    val questionId: Long,
    val questionNumber: Int,
    val wordId: Long,
    val targetText: String,
    val recognizedText: String?,
    val isCorrect: Boolean?,
    val star: Int?,
    val feedback: String?,
    val answeredAt: String?,
)
