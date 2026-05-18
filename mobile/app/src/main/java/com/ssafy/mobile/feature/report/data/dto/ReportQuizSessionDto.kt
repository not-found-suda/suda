package com.ssafy.mobile.feature.report.data.dto

import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.feature.report.domain.model.ReportQuizAnswer
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSession
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionDetail
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

data class ReportQuizSessionDetailResponseDto(
    @SerializedName("sessionId")
    val sessionId: Long,
    @SerializedName("childId")
    val childId: Long,
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
    @SerializedName("totalStar")
    val totalStar: Int? = null,
    @SerializedName("averageStar")
    val averageStar: Double,
    @SerializedName("status")
    val status: String,
    @SerializedName("startedAt")
    val startedAt: String? = null,
    @SerializedName("endedAt")
    val endedAt: String? = null,
    @SerializedName("answers")
    val answers: List<ReportQuizAnswerDto> = emptyList(),
)

data class ReportQuizAnswerDto(
    @SerializedName("questionId")
    val questionId: Long,
    @SerializedName("questionNumber")
    val questionNumber: Int,
    @SerializedName("wordId")
    val wordId: Long,
    @SerializedName("targetText")
    val targetText: String,
    @SerializedName("recognizedText")
    val recognizedText: String? = null,
    @SerializedName("isCorrect")
    val isCorrect: Boolean? = null,
    @SerializedName("star")
    val star: Int? = null,
    @SerializedName("feedback")
    val feedback: String? = null,
    @SerializedName("answeredAt")
    val answeredAt: String? = null,
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

fun ReportQuizSessionDetailResponseDto.toDomain(): ReportQuizSessionDetail =
    ReportQuizSessionDetail(
        sessionId = sessionId,
        childId = childId,
        categoryId = categoryId,
        categoryName = categoryName,
        difficulty = difficulty,
        totalQuestionCount = totalQuestionCount,
        correctCount = correctCount,
        accuracyRate = accuracyRate,
        totalStar = totalStar,
        averageStar = averageStar,
        status = status,
        startedAt = startedAt,
        endedAt = endedAt,
        answers = answers.map { it.toDomain() },
    )

fun ReportQuizAnswerDto.toDomain(): ReportQuizAnswer =
    ReportQuizAnswer(
        questionId = questionId,
        questionNumber = questionNumber,
        wordId = wordId,
        targetText = targetText,
        recognizedText = recognizedText,
        isCorrect = isCorrect,
        star = star,
        feedback = feedback,
        answeredAt = answeredAt,
    )
