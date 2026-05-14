package com.ssafy.mobile.feature.report.presentation

import com.ssafy.mobile.feature.report.domain.model.ReportFilterState
import java.time.LocalDate

internal fun defaultReportFilterState(): ReportFilterState = ReportFilterState()

internal fun ReportFilterInputState.shiftYear(offset: Int): ReportFilterInputState {
    val year = (resolveReportYear() + offset).coerceIn(MIN_REPORT_YEAR, MAX_REPORT_YEAR)
    return copy(
        from = yearStartDate(year),
        to = yearEndDate(year),
    )
}

internal fun ReportFilterInputState.yearLabel(): String =
    if (hasDateRangeFilter()) {
        "${resolveReportYear()}년"
    } else {
        "전체 기간"
    }

internal fun ReportFilterInputState.yearRangeLabel(): String =
    if (hasDateRangeFilter()) {
        "$from ~ $to"
    } else {
        "기간 조건 없이 모든 기록"
    }

internal fun ReportFilterInputState.selectCurrentYear(): ReportFilterInputState =
    LocalDate.now().year.let { year ->
        copy(
            from = yearStartDate(year),
            to = yearEndDate(year),
        )
    }

private fun ReportFilterInputState.hasDateRangeFilter(): Boolean =
    from.isNotBlank() || to.isNotBlank()

private fun ReportFilterInputState.resolveReportYear(): Int =
    from.toReportYearOrNull()
        ?: to.toReportYearOrNull()
        ?: LocalDate.now().year

private fun String.toReportYearOrNull(): Int? =
    take(REPORT_YEAR_LENGTH)
        .toIntOrNull()
        ?.takeIf { year -> year in MIN_REPORT_YEAR..MAX_REPORT_YEAR }

private fun yearStartDate(year: Int): String {
    val paddedYear = year.toString().padStart(REPORT_YEAR_LENGTH, '0')
    return "$paddedYear-01-01"
}

private fun yearEndDate(year: Int): String {
    val paddedYear = year.toString().padStart(REPORT_YEAR_LENGTH, '0')
    return "$paddedYear-12-31"
}

private const val REPORT_YEAR_LENGTH = 4
private const val MIN_REPORT_YEAR = 1
private const val MAX_REPORT_YEAR = 9999
