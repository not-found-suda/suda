package com.ssafy.mobile.feature.report.presentation

import com.ssafy.mobile.feature.report.domain.model.ReportFilterState
import java.time.LocalDate

data class ReportFilterInputState(
    val from: String = "",
    val to: String = "",
    val categoryId: Long? = null,
    val difficulty: String? = null,
    val status: String? = null,
    val minAttemptCount: String = "",
)

data class ReportFilterCategoryOption(
    val categoryId: Long,
    val name: String,
)

data class ReportFilterUiState(
    val input: ReportFilterInputState = defaultReportFilterInputState(),
    val hasAppliedFilter: Boolean = true,
    val errorMessage: String? = null,
    val categoryOptions: List<ReportFilterCategoryOption> = emptyList(),
    val isCategoryLoading: Boolean = false,
    val categoryErrorMessage: String? = null,
)

internal data class ReportFilterPanelConfig(
    val showCategory: Boolean = false,
    val showDifficulty: Boolean = false,
    val showStatus: Boolean = false,
    val showMinAttemptCount: Boolean = false,
)

internal data class ReportFilterActions(
    val onInputChange: (ReportFilterInputState) -> Unit,
    val onApplyClick: () -> Unit,
    val onResetClick: () -> Unit,
    val onRetryCategoriesClick: () -> Unit = {},
)

internal fun ReportFilterInputState.toDateFilter(): Result<ReportFilterState> =
    runCatching {
        val dateRange = validateDateRange()
        ReportFilterState(
            from = dateRange.from,
            to = dateRange.to,
        )
    }

internal fun ReportFilterInputState.toWeakWordsFilter(): Result<ReportFilterState> =
    runCatching {
        val dateRange = validateDateRange()
        ReportFilterState(
            from = dateRange.from,
            to = dateRange.to,
            categoryId = categoryId,
            minAttemptCount = minAttemptCount.toMinAttemptCount(),
        )
    }

internal fun ReportFilterInputState.toQuizSessionsFilter(): Result<ReportFilterState> =
    runCatching {
        val dateRange = validateDateRange()
        ReportFilterState(
            from = dateRange.from,
            to = dateRange.to,
            categoryId = categoryId,
            difficulty = difficulty,
            status = status,
        )
    }

internal fun ReportFilterState.hasFilters(): Boolean =
    !from.isNullOrBlank() ||
        !to.isNullOrBlank() ||
        categoryId != null ||
        !difficulty.isNullOrBlank() ||
        !status.isNullOrBlank() ||
        minAttemptCount != null

private data class DateRange(
    val from: String?,
    val to: String?,
)

private fun ReportFilterInputState.validateDateRange(): DateRange {
    val fromValue = from.trim().ifBlank { null }
    val toValue = to.trim().ifBlank { null }
    val fromDate = fromValue?.toReportFilterDate()
    val toDate = toValue?.toReportFilterDate()

    require(!(fromDate != null && toDate != null && fromDate.isAfter(toDate))) {
        "조회 시작일은 종료일보다 늦을 수 없습니다."
    }

    return DateRange(
        from = fromValue,
        to = toValue,
    )
}

private fun String.toReportFilterDate(): LocalDate {
    require(datePattern.matches(this)) {
        "날짜는 YYYY-MM-DD 형식으로 입력해 주세요."
    }

    return try {
        LocalDate.parse(this)
    } catch (
        @Suppress("TooGenericExceptionCaught")
        e: Exception,
    ) {
        throw IllegalArgumentException("존재하는 날짜를 입력해 주세요.", e)
    }
}

private fun String.toMinAttemptCount(): Int? {
    val value = trim()
    if (value.isBlank()) {
        return null
    }

    val count = value.toIntOrNull()
    require(count != null && count >= MIN_ATTEMPT_COUNT) {
        "최소 풀이 횟수는 1 이상의 숫자로 입력해 주세요."
    }
    return count
}

private const val MIN_ATTEMPT_COUNT = 1
private val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")
