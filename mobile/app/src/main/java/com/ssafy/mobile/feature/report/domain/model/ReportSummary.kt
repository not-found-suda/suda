package com.ssafy.mobile.feature.report.domain.model

data class ReportSummary(
    val childId: Long,
    val participation: ReportParticipationSummary,
    val performance: ReportPerformanceSummary,
    val latestActivity: ReportLatestActivity?,
    val weakWords: List<ReportWeakWord>,
) {
    val hasCompletedRecords: Boolean
        get() = participation.completedSessionCount > 0
}

data class ReportParticipationSummary(
    val completedSessionCount: Int,
    val totalQuestionCount: Int,
)

data class ReportPerformanceSummary(
    val totalCorrectCount: Int,
    val accuracyRate: Double,
    val averageStar: Double,
)

data class ReportLatestActivity(
    val categoryId: Long,
    val categoryName: String,
    val latestSessionAt: String?,
)

data class ReportWeakWord(
    val wordId: Long,
    val word: String,
    val displayText: String?,
    val categoryId: Long,
    val categoryName: String,
    val attemptCount: Int,
    val wrongCount: Int,
    val accuracyRate: Double,
    val averageStar: Double,
    val lastAnsweredAt: String?,
)
