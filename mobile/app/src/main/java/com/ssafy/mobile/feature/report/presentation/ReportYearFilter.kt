@file:Suppress("MagicNumber")

package com.ssafy.mobile.feature.report.presentation

import com.ssafy.mobile.feature.report.domain.model.ReportFilterState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal fun defaultReportFilterState(): ReportFilterState =
    defaultReportFilterInputState()
        .toDateFilter()
        .getOrDefault(ReportFilterState())

internal fun defaultReportFilterInputState(): ReportFilterInputState =
    ReportFilterInputState().applyQuickDateRange(ReportQuickDateRange.CurrentWeek)

internal enum class ReportQuickDateRange(
    val label: String,
) {
    CurrentWeek("이번 주"),
    Recent30Days("최근 30일"),
    All("전체"),
}

internal fun ReportFilterInputState.applyQuickDateRange(
    range: ReportQuickDateRange,
): ReportFilterInputState {
    val today = LocalDate.now()
    return when (range) {
        ReportQuickDateRange.CurrentWeek ->
            copy(
                from =
                    today
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .toReportFilterDateString(),
                to =
                    today
                        .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                        .toReportFilterDateString(),
            )

        ReportQuickDateRange.Recent30Days ->
            copy(
                from = today.minusDays(29).toReportFilterDateString(),
                to = today.toReportFilterDateString(),
            )

        ReportQuickDateRange.All ->
            copy(
                from = "",
                to = "",
            )
    }
}

internal fun ReportFilterInputState.selectedQuickDateRange(): ReportQuickDateRange? =
    ReportQuickDateRange.entries.firstOrNull { range ->
        applyQuickDateRange(range).hasSameDateRangeAs(this)
    }

internal fun ReportFilterInputState.dateRangeLabel(): String =
    when {
        from.isBlank() && to.isBlank() -> "기간 조건 없이 모든 기록"
        from.isBlank() -> "$to 까지"
        to.isBlank() -> "$from 부터"
        else -> "$from ~ $to"
    }

internal fun String.toReportDateOrNull(): LocalDate? =
    runCatching {
        trim()
            .takeIf { it.isNotBlank() }
            ?.let(LocalDate::parse)
    }.getOrNull()

private fun ReportFilterInputState.hasSameDateRangeAs(other: ReportFilterInputState): Boolean =
    from == other.from && to == other.to

private fun LocalDate.toReportFilterDateString(): String = toString()
