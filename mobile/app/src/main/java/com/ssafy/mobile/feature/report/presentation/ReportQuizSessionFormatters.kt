@file:Suppress("MagicNumber", "MatchingDeclarationName")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.ui.graphics.Color
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.feature.report.domain.model.ReportQuizAnswer

data class ReportDifficultyBadgeColors(
    val containerColor: Color,
    val contentColor: Color,
)

internal fun String.toReportDifficultyBadgeColors(): ReportDifficultyBadgeColors =
    when (this) {
        "EASY" ->
            ReportDifficultyBadgeColors(
                containerColor = Color(0xFFE3F2FD), // soft blue
                contentColor = Color(0xFF1E88E5),
            )
        "NORMAL" ->
            ReportDifficultyBadgeColors(
                containerColor = Color(0xFFFFF9C4), // soft yellow
                contentColor = Color(0xFFF57F17),
            )
        "HARD" ->
            ReportDifficultyBadgeColors(
                containerColor = Color(0xFFFFE0B2), // soft orange
                contentColor = Color(0xFFE65100),
            )
        else ->
            ReportDifficultyBadgeColors(
                containerColor = Color(0xFFF5F5F5),
                contentColor = Color(0xFF616161),
            )
    }

internal fun String.toReportDifficultyLabel(): String =
    when (this) {
        "EASY" -> "쉬움"
        "NORMAL" -> "보통"
        "HARD" -> "어려움"
        else -> this
    }

internal fun String.toReportSessionStatusLabel(): String =
    when (this) {
        "COMPLETED" -> "✅ 완료"
        "IN_PROGRESS" -> "⏳ 진행 중"
        "STARTED" -> "⏳ 진행 중"
        "ABANDONED" -> "🛑 중단"
        else -> this
    }

internal fun String.toReportSessionStatusBadgeTone(): AppBadgeTone =
    when (this) {
        "COMPLETED" -> AppBadgeTone.Success
        "IN_PROGRESS" -> AppBadgeTone.Primary
        "STARTED" -> AppBadgeTone.Primary
        "ABANDONED" -> AppBadgeTone.Warning
        else -> AppBadgeTone.Neutral
    }

internal fun ReportQuizAnswer.toReportCorrectnessBadgeTone(): AppBadgeTone =
    when (isCorrect) {
        true -> AppBadgeTone.Success
        false -> AppBadgeTone.Error
        null -> AppBadgeTone.Neutral
    }

internal fun String?.toReportQuizSessionDateLabel(): String =
    if (isNullOrBlank()) {
        "정보 없음"
    } else {
        take(ISO_DATE_LENGTH).replace("-", ".")
    }

internal fun String?.toReportQuizSessionDateTimeLabel(): String {
    if (isNullOrBlank()) {
        return "정보 없음"
    }

    val date = take(ISO_DATE_LENGTH).replace("-", ".")
    val time = drop(ISO_DATE_TIME_SEPARATOR_INDEX).take(ISO_TIME_LENGTH)
    return if (time.length == ISO_TIME_LENGTH) {
        "$date $time"
    } else {
        date
    }
}

private const val ISO_DATE_LENGTH = 10
private const val ISO_DATE_TIME_SEPARATOR_INDEX = 11
private const val ISO_TIME_LENGTH = 5
