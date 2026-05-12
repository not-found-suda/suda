package com.ssafy.mobile.feature.report.data.dto

import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.feature.report.domain.model.ReportLatestActivity
import com.ssafy.mobile.feature.report.domain.model.ReportParticipationSummary
import com.ssafy.mobile.feature.report.domain.model.ReportPerformanceSummary
import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWord

data class ReportSummaryResponseDto(
    @SerializedName("childId")
    val childId: Long,
    @SerializedName("completedSessionCount")
    val completedSessionCount: Int,
    @SerializedName("totalQuestionCount")
    val totalQuestionCount: Int,
    @SerializedName("totalCorrectCount")
    val totalCorrectCount: Int,
    @SerializedName("accuracyRate")
    val accuracyRate: Double,
    @SerializedName("averageStar")
    val averageStar: Double,
    @SerializedName("latestSessionAt")
    val latestSessionAt: String? = null,
    @SerializedName("latestCategory")
    val latestCategory: ReportLatestCategoryDto? = null,
    @SerializedName("weakWords")
    val weakWords: List<ReportWeakWordDto> = emptyList(),
)

data class ReportLatestCategoryDto(
    @SerializedName("categoryId")
    val categoryId: Long,
    @SerializedName("name")
    val name: String,
)

data class ReportWeakWordDto(
    @SerializedName("wordId")
    val wordId: Long,
    @SerializedName("word")
    val word: String,
    @SerializedName("displayText")
    val displayText: String? = null,
    @SerializedName("categoryId")
    val categoryId: Long,
    @SerializedName("categoryName")
    val categoryName: String,
    @SerializedName("attemptCount")
    val attemptCount: Int,
    @SerializedName("wrongCount")
    val wrongCount: Int,
    @SerializedName("accuracyRate")
    val accuracyRate: Double,
    @SerializedName("averageStar")
    val averageStar: Double,
    @SerializedName("lastAnsweredAt")
    val lastAnsweredAt: String? = null,
)

fun ReportSummaryResponseDto.toDomain(): ReportSummary =
    ReportSummary(
        childId = childId,
        participation =
            ReportParticipationSummary(
                completedSessionCount = completedSessionCount,
                totalQuestionCount = totalQuestionCount,
            ),
        performance =
            ReportPerformanceSummary(
                totalCorrectCount = totalCorrectCount,
                accuracyRate = accuracyRate,
                averageStar = averageStar,
            ),
        latestActivity =
            latestCategory?.let { category ->
                ReportLatestActivity(
                    categoryId = category.categoryId,
                    categoryName = category.name,
                    latestSessionAt = latestSessionAt,
                )
            },
        weakWords = weakWords.map { it.toDomain() },
    )

private fun ReportWeakWordDto.toDomain(): ReportWeakWord =
    ReportWeakWord(
        wordId = wordId,
        word = word,
        displayText = displayText,
        categoryId = categoryId,
        categoryName = categoryName,
        attemptCount = attemptCount,
        wrongCount = wrongCount,
        accuracyRate = accuracyRate,
        averageStar = averageStar,
        lastAnsweredAt = lastAnsweredAt,
    )
