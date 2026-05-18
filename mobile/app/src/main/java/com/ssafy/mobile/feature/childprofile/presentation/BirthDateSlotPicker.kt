@file:Suppress("FunctionNaming", "MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.childprofile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun BirthDateSlotPicker(
    birthDate: String,
    enabled: Boolean,
    isError: Boolean,
    supportingText: String?,
    onBirthDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state =
        rememberBirthDateSlotPickerState(
            birthDate = birthDate,
            onBirthDateChange = onBirthDateChange,
        )

    Column(modifier = modifier.fillMaxWidth()) {
        BirthDatePickerLabel(isError = isError)
        Spacer(modifier = Modifier.height(8.dp))
        BirthDateSlotRow(
            state = state,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SelectedDatePill(
            selectedDate = state.selectedDate,
            isError = isError,
        )
        BirthDateSupportingText(
            text = supportingText,
            isError = isError,
        )
    }
}

@Composable
private fun rememberBirthDateSlotPickerState(
    birthDate: String,
    onBirthDateChange: (String) -> Unit,
): BirthDateSlotPickerState {
    val today = remember { LocalDate.now() }
    val defaultDate = remember { today.minusYears(DEFAULT_CHILD_AGE_YEARS.toLong()) }
    val initialDate = remember(birthDate) { birthDate.toLocalDateOrNull() ?: defaultDate }
    var selectedYear by rememberSaveable { mutableIntStateOf(initialDate.year) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(initialDate.monthValue) }
    var selectedDay by rememberSaveable { mutableIntStateOf(initialDate.dayOfMonth) }

    fun updateDate(
        year: Int = selectedYear,
        month: Int = selectedMonth,
        day: Int = selectedDay,
    ) {
        val date = coerceToValidDate(today, year, month, day)
        selectedYear = date.year
        selectedMonth = date.monthValue
        selectedDay = date.dayOfMonth
        onBirthDateChange(date.toStorageDate())
    }

    val slots =
        remember(today, selectedYear, selectedMonth) {
            BirthDateSlots(
                years = ((today.year - MAX_CHILD_AGE_YEARS)..today.year).toList(),
                months = availableMonths(today, selectedYear),
                days = availableDays(today, selectedYear, selectedMonth),
            )
        }

    LaunchedEffect(birthDate) {
        val parsedDate = birthDate.toLocalDateOrNull()
        if (parsedDate != null) {
            selectedYear = parsedDate.year
            selectedMonth = parsedDate.monthValue
            selectedDay = parsedDate.dayOfMonth
        } else if (birthDate.isBlank()) {
            onBirthDateChange(defaultDate.toStorageDate())
        }
    }
    LaunchedEffect(slots.months) {
        if (selectedMonth !in slots.months) updateDate(month = slots.months.last())
    }
    LaunchedEffect(slots.days) {
        if (selectedDay !in slots.days) updateDate(day = slots.days.last())
    }

    return BirthDateSlotPickerState(
        slots = slots,
        selectedYear = selectedYear,
        selectedMonth = selectedMonth,
        selectedDay = selectedDay,
        selectedDate = formatBirthDate(selectedYear, selectedMonth, selectedDay),
        onYearSelected = { updateDate(year = it) },
        onMonthSelected = { updateDate(month = it) },
        onDaySelected = { updateDate(day = it) },
    )
}

@Composable
private fun BirthDatePickerLabel(isError: Boolean) {
    Text(
        text = "생년월일",
        style = MaterialTheme.typography.labelLarge,
        color =
            if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BirthDateSlotRow(
    state: BirthDateSlotPickerState,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateSlotColumn(
            title = "연도",
            values = state.slots.years,
            selectedValue = state.selectedYear,
            enabled = enabled,
            label = { it.toString() },
            onValueSelected = state.onYearSelected,
            modifier = Modifier.weight(1f),
        )
        DateSlotColumn(
            title = "월",
            values = state.slots.months,
            selectedValue = state.selectedMonth,
            enabled = enabled,
            label = { "%02d월".format(it) },
            onValueSelected = state.onMonthSelected,
            modifier = Modifier.weight(1f),
        )
        DateSlotColumn(
            title = "일",
            values = state.slots.days,
            selectedValue = state.selectedDay,
            enabled = enabled,
            label = { "%02d일".format(it) },
            onValueSelected = state.onDaySelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DateSlotColumn(
    title: String,
    values: List<Int>,
    selectedValue: Int,
    enabled: Boolean,
    label: (Int) -> String,
    onValueSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    val firstVisibleIndex = (selectedIndex - SLOT_VISIBLE_SIDE_COUNT).coerceAtLeast(0)
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = firstVisibleIndex,
        )

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(firstVisibleIndex)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        DateSlotList(
            values = values,
            selectedValue = selectedValue,
            enabled = enabled,
            label = label,
            listState = listState,
            onValueSelected = onValueSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DateSlotList(
    values: List<Int>,
    selectedValue: Int,
    enabled: Boolean,
    label: (Int) -> String,
    listState: LazyListState,
    onValueSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(SLOT_LIST_HEIGHT_DP.dp),
        shape = RoundedCornerShape(SLOT_LIST_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SLOT_LIST_CONTAINER_ALPHA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = SLOT_LIST_VERTICAL_PADDING_DP.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(values) { _, value ->
                DateSlotItem(
                    value = value,
                    selected = value == selectedValue,
                    enabled = enabled,
                    label = label,
                    onValueSelected = onValueSelected,
                )
            }
        }
    }
}

@Composable
private fun DateSlotItem(
    value: Int,
    selected: Boolean,
    enabled: Boolean,
    label: (Int) -> String,
    onValueSelected: (Int) -> Unit,
) {
    Text(
        text = label(value),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color =
            if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SLOT_ITEM_DISABLED_ALPHA)
            },
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SLOT_ITEM_HEIGHT_DP.dp)
                .padding(vertical = SLOT_ITEM_VERTICAL_PADDING_DP.dp)
                .clickable(enabled = enabled) {
                    onValueSelected(value)
                },
    )
}

@Composable
private fun SelectedDatePill(
    selectedDate: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
        color =
            if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        contentColor =
            if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
    ) {
        Text(
            text = "선택된 생년월일: $selectedDate",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BirthDateSupportingText(
    text: String?,
    isError: Boolean,
) {
    if (text == null) return

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color =
            if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
}

private fun availableMonths(
    today: LocalDate,
    selectedYear: Int,
): List<Int> {
    val maxMonth = if (selectedYear == today.year) today.monthValue else MONTHS_IN_YEAR
    return (1..maxMonth).toList()
}

private fun availableDays(
    today: LocalDate,
    selectedYear: Int,
    selectedMonth: Int,
): List<Int> {
    val maxDayOfMonth = YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()
    val maxDay =
        if (selectedYear == today.year && selectedMonth == today.monthValue) {
            today.dayOfMonth
        } else {
            maxDayOfMonth
        }
    return (1..maxDay).toList()
}

private fun coerceToValidDate(
    today: LocalDate,
    year: Int,
    month: Int,
    day: Int,
): LocalDate {
    val maxMonth = if (year == today.year) today.monthValue else MONTHS_IN_YEAR
    val nextMonth = month.coerceIn(1, maxMonth)
    val maxDayOfMonth = YearMonth.of(year, nextMonth).lengthOfMonth()
    val maxDay =
        if (year == today.year && nextMonth == today.monthValue) {
            today.dayOfMonth
        } else {
            maxDayOfMonth
        }
    return LocalDate.of(year, nextMonth, day.coerceIn(1, maxDay))
}

private fun String.toLocalDateOrNull(): LocalDate? {
    val normalizedDate =
        when {
            matches(Regex("""^\d{4}-\d{2}-\d{2}$""")) -> this
            matches(Regex("""^\d{8}$""")) ->
                "%s-%s-%s".format(
                    substring(YEAR_START, YEAR_END),
                    substring(MONTH_START, MONTH_END),
                    substring(DAY_START, DAY_END),
                )
            else -> return null
        }

    return runCatching {
        LocalDate.parse(normalizedDate, DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()
}

private fun LocalDate.toStorageDate(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun formatBirthDate(
    year: Int,
    month: Int,
    day: Int,
): String = "%04d-%02d-%02d".format(year, month, day)

private data class BirthDateSlots(
    val years: List<Int>,
    val months: List<Int>,
    val days: List<Int>,
)

private data class BirthDateSlotPickerState(
    val slots: BirthDateSlots,
    val selectedYear: Int,
    val selectedMonth: Int,
    val selectedDay: Int,
    val selectedDate: String,
    val onYearSelected: (Int) -> Unit,
    val onMonthSelected: (Int) -> Unit,
    val onDaySelected: (Int) -> Unit,
)

private const val DEFAULT_CHILD_AGE_YEARS = 3
private const val MAX_CHILD_AGE_YEARS = 18
private const val MONTHS_IN_YEAR = 12
private const val YEAR_START = 0
private const val YEAR_END = 4
private const val MONTH_START = 4
private const val MONTH_END = 6
private const val DAY_START = 6
private const val DAY_END = 8
private const val SLOT_VISIBLE_SIDE_COUNT = 1
private const val SLOT_LIST_HEIGHT_DP = 132
private const val SLOT_LIST_RADIUS_DP = 14
private const val SLOT_LIST_CONTAINER_ALPHA = 0.55f
private const val SLOT_LIST_VERTICAL_PADDING_DP = 38
private const val SLOT_ITEM_DISABLED_ALPHA = 0.46f
private const val SLOT_ITEM_HEIGHT_DP = 40
private const val SLOT_ITEM_VERTICAL_PADDING_DP = 6
private const val PILL_RADIUS_DP = 999
