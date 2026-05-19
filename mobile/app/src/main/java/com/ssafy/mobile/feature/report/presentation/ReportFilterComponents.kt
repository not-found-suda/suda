@file:Suppress("ComplexCondition", "MagicNumber", "MaxLineLength", "TooManyFunctions")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.AppTextField
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun ReportFilterPanel(
    state: ReportFilterUiState,
    config: ReportFilterPanelConfig,
    actions: ReportFilterActions,
    modifier: Modifier = Modifier,
) {
    ReportGlassCard(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ReportFilterHeader(state = state)
            ReportDateRangePicker(
                input = state.input,
                isError = state.errorMessage != null,
                onInputChange = actions.onInputChange,
            )
            ReportFilterOptionSection(
                state = state,
                config = config,
                actions = actions,
            )
            state.errorMessage?.let { message ->
                AppBadge(
                    text = message,
                    tone = AppBadgeTone.Error,
                )
            }
            ReportFilterActionRow(
                onApplyClick = actions.onApplyClick,
                onResetClick = actions.onResetClick,
            )
        }
    }
}

@Composable
private fun ReportFilterHeader(state: ReportFilterUiState) {
    val currentRange = state.input.selectedQuickDateRange()
    val badgeText =
        when (currentRange) {
            ReportQuickDateRange.CurrentWeek -> "이번주"
            ReportQuickDateRange.Recent30Days -> "이번달"
            ReportQuickDateRange.All -> "전체"
            null -> "직접 설정"
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "조회 기간",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.input.dateRangeLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        AppBadge(
            text = badgeText,
            tone =
                if (state.hasAppliedFilter ||
                    currentRange != ReportQuickDateRange.All
                ) {
                    AppBadgeTone.Primary
                } else {
                    AppBadgeTone.Neutral
                },
        )
    }
}

@Composable
private fun ReportFilterOptionSection(
    state: ReportFilterUiState,
    config: ReportFilterPanelConfig,
    actions: ReportFilterActions,
) {
    if (
        !config.showCategory &&
        !config.showDifficulty &&
        !config.showStatus &&
        !config.showMinAttemptCount
    ) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (config.showCategory) {
            ReportCategoryFilter(
                state = state,
                onInputChange = actions.onInputChange,
                onRetryClick = actions.onRetryCategoriesClick,
            )
        }
        if (config.showDifficulty) {
            ReportOptionDropdown(
                label = "난이도",
                selectedLabel =
                    difficultyOptions
                        .firstOrNull { it.value == state.input.difficulty }
                        ?.label ?: "전체 난이도",
                options = difficultyOptions,
                onSelected = { value ->
                    actions.onInputChange(state.input.copy(difficulty = value))
                },
            )
        }
        if (config.showStatus) {
            ReportOptionDropdown(
                label = "상태",
                selectedLabel =
                    statusOptions
                        .firstOrNull { it.value == state.input.status }
                        ?.label ?: "전체 상태",
                options = statusOptions,
                onSelected = { value ->
                    actions.onInputChange(state.input.copy(status = value))
                },
            )
        }
        if (config.showMinAttemptCount) {
            AppTextField(
                value = state.input.minAttemptCount,
                onValueChange = { value ->
                    actions.onInputChange(state.input.copy(minAttemptCount = value))
                },
                label = "최소 풀이 횟수",
                isError = state.errorMessage != null,
                supportingText = "1 이상",
            )
        }
    }
}

@Composable
private fun ReportDateRangePicker(
    input: ReportFilterInputState,
    isError: Boolean,
    onInputChange: (ReportFilterInputState) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "기간",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            ReportQuickPeriodRows(
                input = input,
                onInputChange = onInputChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportDateField(
                    label = "시작일",
                    value = input.from,
                    isError = isError,
                    onDateSelected = { selectedDate ->
                        onInputChange(input.copy(from = selectedDate))
                    },
                    modifier = Modifier.weight(1f),
                )
                ReportDateField(
                    label = "종료일",
                    value = input.to,
                    isError = isError,
                    onDateSelected = { selectedDate ->
                        onInputChange(input.copy(to = selectedDate))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReportQuickPeriodRows(
    input: ReportFilterInputState,
    onInputChange: (ReportFilterInputState) -> Unit,
) {
    val selectedRange = input.selectedQuickDateRange()
    val ranges = ReportQuickDateRange.entries

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ranges.forEach { range ->
            ReportQuickPeriodButton(
                text = range.label,
                selected = selectedRange == range,
                onClick = { onInputChange(input.applyQuickDateRange(range)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReportDateField(
    label: String,
    value: String,
    isError: Boolean,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var isCalendarOpen by remember { mutableStateOf(false) }

    Surface(
        onClick = { isCalendarOpen = true },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        contentColor = colors.onSurface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value.ifBlank { "날짜 선택" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    when {
                        isError -> colors.error
                        value.isBlank() -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (isCalendarOpen) {
        ReportCalendarDialog(
            title = "$label 선택",
            initialDate = value.toReportDateOrNull() ?: LocalDate.now(),
            onDismiss = { isCalendarOpen = false },
            onDateSelected = { selectedDate ->
                isCalendarOpen = false
                onDateSelected(selectedDate)
            },
        )
    }
}

@Composable
private fun ReportCalendarDialog(
    title: String,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit,
) {
    var currentMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    var pickerMode by remember { mutableStateOf(ReportCalendarPickerMode.Day) }
    val days = remember(currentMonth) { currentMonth.toCalendarDays() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ReportCalendarTitle(
                    title = title,
                    month = currentMonth,
                    onPreviousMonthClick = { currentMonth = currentMonth.minusMonths(1) },
                    onNextMonthClick = { currentMonth = currentMonth.plusMonths(1) },
                    onYearClick = { pickerMode = ReportCalendarPickerMode.Year },
                    onMonthClick = { pickerMode = ReportCalendarPickerMode.Month },
                )
                when (pickerMode) {
                    ReportCalendarPickerMode.Day -> {
                        ReportCalendarWeekdays()
                        ReportCalendarMonthGrid(
                            days = days,
                            selectedDate = initialDate,
                            onDateSelected = onDateSelected,
                        )
                    }

                    ReportCalendarPickerMode.Year ->
                        ReportCalendarYearPicker(
                            selectedYear = currentMonth.year,
                            onYearSelected = { year ->
                                currentMonth = YearMonth.of(year, currentMonth.monthValue)
                                pickerMode = ReportCalendarPickerMode.Day
                            },
                        )

                    ReportCalendarPickerMode.Month ->
                        ReportCalendarMonthPicker(
                            selectedMonth = currentMonth.monthValue,
                            onMonthSelected = { month ->
                                currentMonth = YearMonth.of(currentMonth.year, month)
                                pickerMode = ReportCalendarPickerMode.Day
                            },
                        )
                }
            }
        }
    }
}

@Composable
private fun ReportCalendarTitle(
    title: String,
    month: YearMonth,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onYearClick: () -> Unit,
    onMonthClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportCalendarNavButton(
                text = "‹",
                onClick = onPreviousMonthClick,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportCalendarTitleButton(
                    text = "${month.year}년",
                    onClick = onYearClick,
                )
                ReportCalendarTitleButton(
                    text = "${month.monthValue}월",
                    onClick = onMonthClick,
                )
            }
            ReportCalendarNavButton(
                text = "›",
                onClick = onNextMonthClick,
            )
        }
    }
}

@Composable
private fun ReportCalendarTitleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReportCalendarNavButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ReportCalendarWeekdays() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        calendarWeekdayLabels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReportCalendarMonthGrid(
    days: List<LocalDate?>,
    selectedDate: LocalDate,
    onDateSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.chunked(CALENDAR_DAYS_IN_WEEK).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { date ->
                    ReportCalendarDayCell(
                        date = date,
                        selected = date == selectedDate,
                        onDateSelected = onDateSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportCalendarYearPicker(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CALENDAR_PICKER_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(calendarYearRange.toList()) { year ->
            ReportCalendarPickerRow(
                text = "${year}년",
                selected = year == selectedYear,
                onClick = { onYearSelected(year) },
            )
        }
    }
}

@Composable
private fun ReportCalendarMonthPicker(
    selectedMonth: Int,
    onMonthSelected: (Int) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CALENDAR_PICKER_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items((1..MONTHS_IN_YEAR).toList()) { month ->
            ReportCalendarPickerRow(
                text = "${month}월",
                selected = month == selectedMonth,
                onClick = { onMonthSelected(month) },
            )
        }
    }
}

@Composable
private fun ReportCalendarPickerRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.52f),
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReportCalendarDayCell(
    date: LocalDate?,
    selected: Boolean,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val cellColor =
        when {
            selected -> colors.primary
            date != null -> colors.surfaceVariant.copy(alpha = 0.48f)
            else -> colors.surface
        }
    val contentColor =
        when {
            selected -> colors.onPrimary
            date != null -> colors.onSurface
            else -> colors.surface
        }

    Surface(
        onClick = {
            date?.let { onDateSelected(it.toString()) }
        },
        modifier =
            modifier
                .height(40.dp),
        enabled = date != null,
        shape = RoundedCornerShape(8.dp),
        color = cellColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = date?.dayOfMonth?.toString().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReportQuickPeriodButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        tonalElevation = if (selected) 0.dp else 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReportCategoryFilter(
    state: ReportFilterUiState,
    onInputChange: (ReportFilterInputState) -> Unit,
    onRetryClick: () -> Unit,
) {
    val selectedCategory =
        state.categoryOptions.firstOrNull { option ->
            option.categoryId == state.input.categoryId
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportCategoryDropdown(
            selectedLabel = selectedCategory?.name ?: "전체 카테고리",
            options = state.categoryOptions,
            onSelected = { categoryId ->
                onInputChange(state.input.copy(categoryId = categoryId))
            },
        )
        if (state.isCategoryLoading) {
            AppBadge(
                text = "카테고리를 불러오는 중...",
                tone = AppBadgeTone.Neutral,
            )
        }
        state.categoryErrorMessage?.let { message ->
            AppBadge(
                text = message,
                tone = AppBadgeTone.Error,
            )
            AppSecondaryButton(
                text = "카테고리 다시 불러오기",
                onClick = onRetryClick,
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

@Composable
private fun ReportCategoryDropdown(
    selectedLabel: String,
    options: List<ReportFilterCategoryOption>,
    onSelected: (Long?) -> Unit,
) {
    ReportDropdownField(
        label = "카테고리",
        selectedLabel = selectedLabel,
        menuContent = { close ->
            DropdownMenuItem(
                text = { Text(text = "전체 카테고리") },
                onClick = {
                    close()
                    onSelected(null)
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.name) },
                    onClick = {
                        close()
                        onSelected(option.categoryId)
                    },
                )
            }
        },
    )
}

@Composable
private fun ReportOptionDropdown(
    label: String,
    selectedLabel: String,
    options: List<ReportFilterSelectionOption>,
    onSelected: (String?) -> Unit,
) {
    ReportDropdownField(
        label = label,
        selectedLabel = selectedLabel,
        menuContent = { close ->
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label) },
                    onClick = {
                        close()
                        onSelected(option.value)
                    },
                )
            }
        },
    )
}

@Composable
private fun ReportDropdownField(
    label: String,
    selectedLabel: String,
    menuContent: @Composable (close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            menuContent { expanded = false }
        }
    }
}

@Composable
private fun ReportFilterActionRow(
    onApplyClick: () -> Unit,
    onResetClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSecondaryButton(
            text = "초기화",
            onClick = onResetClick,
            modifier =
                Modifier
                    .weight(1f)
                    .height(42.dp),
        )
        AppPrimaryButton(
            text = "적용",
            onClick = onApplyClick,
            modifier =
                Modifier
                    .weight(1f)
                    .height(42.dp),
        )
    }
}

private data class ReportFilterSelectionOption(
    val value: String?,
    val label: String,
)

private val difficultyOptions =
    listOf(
        ReportFilterSelectionOption(value = null, label = "전체 난이도"),
        ReportFilterSelectionOption(value = "EASY", label = "쉬움"),
        ReportFilterSelectionOption(value = "NORMAL", label = "보통"),
        ReportFilterSelectionOption(value = "HARD", label = "어려움"),
    )

private val statusOptions =
    listOf(
        ReportFilterSelectionOption(value = null, label = "전체 상태"),
        ReportFilterSelectionOption(value = "COMPLETED", label = "완료"),
        ReportFilterSelectionOption(value = "STARTED", label = "진행 중"),
        ReportFilterSelectionOption(value = "ABANDONED", label = "중단"),
    )

private fun YearMonth.toCalendarDays(): List<LocalDate?> {
    val leadingBlankCount = atDay(1).dayOfWeek.value % CALENDAR_DAYS_IN_WEEK
    val monthDates = (1..lengthOfMonth()).map { day -> atDay(day) }
    val trailingBlankCount = CALENDAR_TOTAL_CELL_COUNT - leadingBlankCount - monthDates.size

    return buildList {
        repeat(leadingBlankCount) { add(null) }
        addAll(monthDates)
        repeat(trailingBlankCount) { add(null) }
    }
}

private enum class ReportCalendarPickerMode {
    Day,
    Year,
    Month,
}

private val calendarWeekdayLabels = listOf("일", "월", "화", "수", "목", "금", "토")
private val calendarYearRange = 2020..LocalDate.now().year + 1
private val CALENDAR_PICKER_HEIGHT = 270.dp
private const val CALENDAR_DAYS_IN_WEEK = 7
private const val CALENDAR_TOTAL_CELL_COUNT = 42
private const val MONTHS_IN_YEAR = 12
