package com.ssafy.mobile.feature.report.data.dto

import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgress
import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgressPage

data class ReportCategoryProgressListResponseDto(
    @SerializedName("categories")
    val categories: List<ReportCategoryProgressDto> = emptyList(),
)

data class ReportCategoryProgressDto(
    @SerializedName("categoryId")
    val categoryId: Long,
    @SerializedName("categoryName")
    val categoryName: String,
    @SerializedName("totalWordCount")
    val totalWordCount: Long,
    @SerializedName("quizzedWordCount")
    val quizzedWordCount: Long,
    @SerializedName("correctWordCount")
    val correctWordCount: Long,
    @SerializedName("quizCoverageRate")
    val quizCoverageRate: Double,
    @SerializedName("correctWordRate")
    val correctWordRate: Double,
    @SerializedName("completedSessionCount")
    val completedSessionCount: Long,
    @SerializedName("totalQuestionCount")
    val totalQuestionCount: Long,
    @SerializedName("correctCount")
    val correctCount: Long,
    @SerializedName("accuracyRate")
    val accuracyRate: Double,
    @SerializedName("averageStar")
    val averageStar: Double,
    @SerializedName("latestSessionAt")
    val latestSessionAt: String? = null,
)

fun ReportCategoryProgressListResponseDto.toDomain(): ReportCategoryProgressPage =
    ReportCategoryProgressPage(
        categories = categories.map { it.toDomain() },
    )

fun ReportCategoryProgressDto.toDomain(): ReportCategoryProgress =
    ReportCategoryProgress(
        categoryId = categoryId,
        categoryName = categoryName,
        totalWordCount = totalWordCount,
        quizzedWordCount = quizzedWordCount,
        correctWordCount = correctWordCount,
        quizCoverageRate = quizCoverageRate,
        correctWordRate = correctWordRate,
        completedSessionCount = completedSessionCount,
        totalQuestionCount = totalQuestionCount,
        correctCount = correctCount,
        accuracyRate = accuracyRate,
        averageStar = averageStar,
        latestSessionAt = latestSessionAt,
    )
