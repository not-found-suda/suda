package com.ssafy.mobile.feature.report.data.dto

import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSession
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionPage

data class ReportQuizSessionListResponseDto(
    @SerializedName("content")
    val content: List<ReportQuizSessionDto> = emptyList(),
    @SerializedName("page")
    val page: Int,
    @SerializedName("size")
    val size: Int,
    @SerializedName("totalElements")
    val totalElements: Long,
    @SerializedName("totalPages")
    val totalPages: Int,
)

data class ReportQuizSessionDto(
    @SerializedName("sessionId")
    val sessionId: Long,
    @SerializedName("categoryId")
    val categoryId: Long,
    @SerializedName("categoryName")
    val categoryName: String,
    @SerializedName("difficulty")
    val difficulty: String,
    @SerializedName("totalQuestionCount")
    val totalQuestionCount: Int,
    @SerializedName("correctCount")
    val correctCount: Int,
    @SerializedName("accuracyRate")
    val accuracyRate: Double,
    @SerializedName("averageStar")
    val averageStar: Double,
    @SerializedName("status")
    val status: String,
    @SerializedName("startedAt")
    val startedAt: String? = null,
    @SerializedName("endedAt")
    val endedAt: String? = null,
)

fun ReportQuizSessionListResponseDto.toDomain(): ReportQuizSessionPage =
    ReportQuizSessionPage(
        sessions = content.map { it.toDomain() },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )

fun ReportQuizSessionDto.toDomain(): ReportQuizSession =
    ReportQuizSession(
        sessionId = sessionId,
        categoryId = categoryId,
        categoryName = categoryName,
        difficulty = difficulty,
        totalQuestionCount = totalQuestionCount,
        correctCount = correctCount,
        accuracyRate = accuracyRate,
        averageStar = averageStar,
        status = status,
        startedAt = startedAt,
        endedAt = endedAt,
    )
