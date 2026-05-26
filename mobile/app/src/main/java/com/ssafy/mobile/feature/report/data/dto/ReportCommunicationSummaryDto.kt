package com.ssafy.mobile.feature.report.data.dto

import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationAnalysisStatus
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationSessionSummary
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationSummary
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationWordCount
import com.ssafy.mobile.feature.report.domain.model.ReportExpressionTypeCounts
import java.util.Locale

data class ReportCommunicationSummaryResponseDto(
    @SerializedName("childId")
    val childId: Long,
    @SerializedName("analysisStatus")
    val analysisStatus: String? = null,
    @SerializedName("totalSessionCount")
    val totalSessionCount: Long = 0,
    @SerializedName("totalUtteranceCount")
    val totalUtteranceCount: Long = 0,
    @SerializedName("averageSentenceLength")
    val averageSentenceLength: Double = 0.0,
    @SerializedName("frequentWords")
    val frequentWords: List<ReportCommunicationWordCountDto> = emptyList(),
    @SerializedName("expressionTypeCounts")
    val expressionTypeCounts: ReportExpressionTypeCountsDto? = null,
    @SerializedName("communicationLevel")
    val communicationLevel: String? = null,
    @SerializedName("vocabularyDiversityLevel")
    val vocabularyDiversityLevel: String? = null,
    @SerializedName("sentenceExpansionLevel")
    val sentenceExpansionLevel: String? = null,
    @SerializedName("strengths")
    val strengths: List<String> = emptyList(),
    @SerializedName("improvementPoints")
    val improvementPoints: List<String> = emptyList(),
    @SerializedName("parentGuide")
    val parentGuide: List<String> = emptyList(),
    @SerializedName("recommendedActivities")
    val recommendedActivities: List<String> = emptyList(),
    @SerializedName("developmentReference")
    val developmentReference: String? = null,
    @SerializedName("cautionLevel")
    val cautionLevel: String? = null,
    @SerializedName("consultationGuide")
    val consultationGuide: String? = null,
    @SerializedName("recentSessions")
    val recentSessions: List<ReportCommunicationSessionSummaryDto> = emptyList(),
    @SerializedName("generatedAt")
    val generatedAt: String? = null,
)

data class ReportCommunicationSessionSummaryDto(
    @SerializedName("sessionId")
    val sessionId: Long,
    @SerializedName("startedAt")
    val startedAt: String? = null,
    @SerializedName("endedAt")
    val endedAt: String? = null,
    @SerializedName("utteranceCount")
    val utteranceCount: Int = 0,
    @SerializedName("averageSentenceLength")
    val averageSentenceLength: Double = 0.0,
    @SerializedName("frequentWords")
    val frequentWords: List<ReportCommunicationWordCountDto> = emptyList(),
    @SerializedName("expressionTypeCounts")
    val expressionTypeCounts: ReportExpressionTypeCountsDto? = null,
    @SerializedName("communicationLevel")
    val communicationLevel: String? = null,
    @SerializedName("vocabularyDiversityLevel")
    val vocabularyDiversityLevel: String? = null,
    @SerializedName("sentenceExpansionLevel")
    val sentenceExpansionLevel: String? = null,
    @SerializedName("strengths")
    val strengths: List<String> = emptyList(),
    @SerializedName("improvementPoints")
    val improvementPoints: List<String> = emptyList(),
    @SerializedName("parentGuide")
    val parentGuide: List<String> = emptyList(),
    @SerializedName("recommendedActivities")
    val recommendedActivities: List<String> = emptyList(),
    @SerializedName("developmentReference")
    val developmentReference: String? = null,
    @SerializedName("cautionLevel")
    val cautionLevel: String? = null,
    @SerializedName("consultationGuide")
    val consultationGuide: String? = null,
    @SerializedName("summary")
    val summary: String? = null,
    @SerializedName("analysisStatus")
    val analysisStatus: String? = null,
    @SerializedName("analyzedAt")
    val analyzedAt: String? = null,
)

data class ReportCommunicationWordCountDto(
    @SerializedName("word")
    val word: String? = null,
    @SerializedName("count")
    val count: Int = 0,
)

data class ReportExpressionTypeCountsDto(
    @SerializedName("request")
    val request: Int = 0,
    @SerializedName("emotion")
    val emotion: Int = 0,
    @SerializedName("response")
    val response: Int = 0,
    @SerializedName("play")
    val play: Int = 0,
    @SerializedName("question")
    val question: Int = 0,
    @SerializedName("other")
    val other: Int = 0,
)

fun ReportCommunicationSummaryResponseDto.toDomain(): ReportCommunicationSummary =
    ReportCommunicationSummary(
        childId = childId,
        analysisStatus = analysisStatus.toCommunicationAnalysisStatus(),
        totalSessionCount = totalSessionCount,
        totalUtteranceCount = totalUtteranceCount,
        averageSentenceLength = averageSentenceLength,
        frequentWords = frequentWords.mapNotNull { it.toDomainOrNull() },
        expressionTypeCounts =
            expressionTypeCounts?.toDomain()
                ?: ReportExpressionTypeCounts(
                    request = 0,
                    emotion = 0,
                    response = 0,
                    play = 0,
                    question = 0,
                    other = 0,
                ),
        communicationLevel = communicationLevel.orUnknownLevel(),
        vocabularyDiversityLevel = vocabularyDiversityLevel.orUnknownLevel(),
        sentenceExpansionLevel = sentenceExpansionLevel.orUnknownLevel(),
        strengths = strengths.filterNotBlank(),
        improvementPoints = improvementPoints.filterNotBlank(),
        parentGuide = parentGuide.filterNotBlank(),
        recommendedActivities = recommendedActivities.filterNotBlank(),
        developmentReference = developmentReference.orEmpty(),
        cautionLevel = cautionLevel.orNoneCautionLevel(),
        consultationGuide = consultationGuide.orEmpty(),
        recentSessions = recentSessions.map { it.toDomain() },
        generatedAt = generatedAt,
    )

fun ReportCommunicationSessionSummaryDto.toDomain(): ReportCommunicationSessionSummary =
    ReportCommunicationSessionSummary(
        sessionId = sessionId,
        startedAt = startedAt,
        endedAt = endedAt,
        utteranceCount = utteranceCount,
        averageSentenceLength = averageSentenceLength,
        frequentWords = frequentWords.mapNotNull { it.toDomainOrNull() },
        expressionTypeCounts =
            expressionTypeCounts?.toDomain()
                ?: ReportExpressionTypeCounts(
                    request = 0,
                    emotion = 0,
                    response = 0,
                    play = 0,
                    question = 0,
                    other = 0,
                ),
        communicationLevel = communicationLevel.orUnknownLevel(),
        vocabularyDiversityLevel = vocabularyDiversityLevel.orUnknownLevel(),
        sentenceExpansionLevel = sentenceExpansionLevel.orUnknownLevel(),
        strengths = strengths.filterNotBlank(),
        improvementPoints = improvementPoints.filterNotBlank(),
        parentGuide = parentGuide.filterNotBlank(),
        recommendedActivities = recommendedActivities.filterNotBlank(),
        developmentReference = developmentReference.orEmpty(),
        cautionLevel = cautionLevel.orNoneCautionLevel(),
        consultationGuide = consultationGuide.orEmpty(),
        summary = summary.orEmpty(),
        analysisStatus = analysisStatus.toCommunicationAnalysisStatus(),
        analyzedAt = analyzedAt,
    )

fun ReportExpressionTypeCountsDto.toDomain(): ReportExpressionTypeCounts =
    ReportExpressionTypeCounts(
        request = request,
        emotion = emotion,
        response = response,
        play = play,
        question = question,
        other = other,
    )

private fun ReportCommunicationWordCountDto.toDomainOrNull(): ReportCommunicationWordCount? {
    val safeWord = word?.trim().orEmpty()
    if (safeWord.isBlank()) {
        return null
    }
    return ReportCommunicationWordCount(
        word = safeWord,
        count = count,
    )
}

private fun String?.toCommunicationAnalysisStatus(): ReportCommunicationAnalysisStatus =
    when (this?.uppercase(Locale.ROOT)) {
        "PENDING" -> ReportCommunicationAnalysisStatus.Pending
        "PROCESSING" -> ReportCommunicationAnalysisStatus.Processing
        "COMPLETED" -> ReportCommunicationAnalysisStatus.Completed
        "FAILED" -> ReportCommunicationAnalysisStatus.Failed
        "EMPTY" -> ReportCommunicationAnalysisStatus.Empty
        else -> ReportCommunicationAnalysisStatus.Unknown
    }

private fun String?.orUnknownLevel(): String =
    when (this?.uppercase(Locale.ROOT)) {
        "LOW" -> "LOW"
        "NORMAL" -> "NORMAL"
        "HIGH" -> "HIGH"
        else -> "UNKNOWN"
    }

private fun String?.orNoneCautionLevel(): String =
    when (this?.uppercase(Locale.ROOT)) {
        "WATCH" -> "WATCH"
        "CONSULT" -> "CONSULT"
        else -> "NONE"
    }

private fun List<String>.filterNotBlank(): List<String> =
    map { it.trim() }
        .filter { it.isNotBlank() }
