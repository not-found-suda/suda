package com.ssafy.mobile.feature.report.domain.model

data class ReportSummary(
    val childId: Long,
    val participation: ReportParticipationSummary,
    val performance: ReportPerformanceSummary,
    val latestActivity: ReportLatestActivity?,
    val weakWords: List<ReportWeakWord>,
    val generatedAt: String?,
) {
    val hasCompletedRecords: Boolean
        get() = participation.completedSessionCount > 0
}

data class ReportParticipationSummary(
    val completedSessionCount: Long,
    val totalQuestionCount: Long,
)

data class ReportPerformanceSummary(
    val totalCorrectCount: Long,
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
    val attemptCount: Long,
    val wrongCount: Long,
    val accuracyRate: Double,
    val averageStar: Double,
    val lastAnsweredAt: String?,
)

data class ReportWeakWordPage(
    val words: List<ReportWeakWord>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    val hasWords: Boolean
        get() = words.isNotEmpty()
}
