package com.ssafy.mobile.feature.report.domain.model

data class ReportCommunicationSummary(
    val childId: Long,
    val analysisStatus: ReportCommunicationAnalysisStatus,
    val totalSessionCount: Long,
    val totalUtteranceCount: Long,
    val averageSentenceLength: Double,
    val frequentWords: List<ReportCommunicationWordCount>,
    val expressionTypeCounts: ReportExpressionTypeCounts,
    val communicationLevel: String,
    val vocabularyDiversityLevel: String,
    val sentenceExpansionLevel: String,
    val strengths: List<String>,
    val improvementPoints: List<String>,
    val parentGuide: List<String>,
    val recommendedActivities: List<String>,
    val developmentReference: String,
    val cautionLevel: String,
    val consultationGuide: String,
    val recentSessions: List<ReportCommunicationSessionSummary>,
    val generatedAt: String?,
) {
    val hasCommunicationData: Boolean
        get() = totalSessionCount > 0 || totalUtteranceCount > 0 || recentSessions.isNotEmpty()
}

data class ReportCommunicationWordCount(
    val word: String,
    val count: Int,
)

data class ReportExpressionTypeCounts(
    val request: Int,
    val emotion: Int,
    val response: Int,
    val play: Int,
    val question: Int,
    val other: Int,
) {
    val total: Int
        get() = request + emotion + response + play + question + other
}

data class ReportCommunicationSessionSummary(
    val sessionId: Long,
    val startedAt: String?,
    val endedAt: String?,
    val utteranceCount: Int,
    val averageSentenceLength: Double,
    val frequentWords: List<ReportCommunicationWordCount>,
    val expressionTypeCounts: ReportExpressionTypeCounts,
    val communicationLevel: String,
    val vocabularyDiversityLevel: String,
    val sentenceExpansionLevel: String,
    val strengths: List<String>,
    val improvementPoints: List<String>,
    val parentGuide: List<String>,
    val recommendedActivities: List<String>,
    val developmentReference: String,
    val cautionLevel: String,
    val consultationGuide: String,
    val summary: String,
    val analysisStatus: ReportCommunicationAnalysisStatus,
    val analyzedAt: String?,
)

enum class ReportCommunicationAnalysisStatus {
    Pending,
    Processing,
    Completed,
    Failed,
    Empty,
    Unknown,
}
