package com.ssafy.mobile.feature.report.domain.model

data class ReportCategoryProgressPage(
    val categories: List<ReportCategoryProgress>,
) {
    val hasCategories: Boolean
        get() = categories.isNotEmpty()
}

data class ReportCategoryProgress(
    val categoryId: Long,
    val categoryName: String,
    val totalWordCount: Long,
    val quizzedWordCount: Long,
    val correctWordCount: Long,
    val quizCoverageRate: Double,
    val correctWordRate: Double,
    val completedSessionCount: Long,
    val totalQuestionCount: Long,
    val correctCount: Long,
    val accuracyRate: Double,
    val averageStar: Double,
    val latestSessionAt: String?,
)
