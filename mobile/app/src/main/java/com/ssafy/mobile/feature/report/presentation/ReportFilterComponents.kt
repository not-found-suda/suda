package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.AppTextField

@Composable
internal fun ReportFilterPanel(
    state: ReportFilterUiState,
    config: ReportFilterPanelConfig,
    actions: ReportFilterActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = REPORT_FILTER_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "필터",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            ReportDateRangeFields(
                input = state.input,
                onInputChange = actions.onInputChange,
            )
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
                        difficultyOptions.firstOrNull { it.value == state.input.difficulty }?.label
                            ?: "전체 난이도",
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
                        statusOptions.firstOrNull { it.value == state.input.status }?.label
                            ?: "전체 상태",
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
                    supportingText = "1 이상",
                )
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
private fun ReportDateRangeFields(
    input: ReportFilterInputState,
    onInputChange: (ReportFilterInputState) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTextField(
            value = input.from,
            onValueChange = { value -> onInputChange(input.copy(from = value)) },
            label = "시작일",
            modifier = Modifier.weight(1f),
            supportingText = "YYYY-MM-DD",
        )
        AppTextField(
            value = input.to,
            onValueChange = { value -> onInputChange(input.copy(to = value)) },
            label = "종료일",
            modifier = Modifier.weight(1f),
            supportingText = "YYYY-MM-DD",
        )
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
    ReportCategoryDropdown(
        selectedLabel = selectedCategory?.name ?: "전체 카테고리",
        options = state.categoryOptions,
        onSelected = { categoryId ->
            onInputChange(state.input.copy(categoryId = categoryId))
        },
    )

    if (state.isCategoryLoading) {
        Text(
            text = "카테고리를 불러오는 중...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.categoryErrorMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        AppSecondaryButton(
            text = "카테고리 다시 불러오기",
            onClick = onRetryClick,
            modifier = Modifier.height(36.dp),
        )
    }
}

@Composable
private fun ReportCategoryDropdown(
    selectedLabel: String,
    options: List<ReportFilterCategoryOption>,
    onSelected: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "전체 카테고리") },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.name) },
                    onClick = {
                        expanded = false
                        onSelected(option.categoryId)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReportOptionDropdown(
    label: String,
    selectedLabel: String,
    options: List<ReportFilterSelectionOption>,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "$label: $selectedLabel")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label) },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReportFilterActionRow(
    onApplyClick: () -> Unit,
    onResetClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        AppSecondaryButton(
            text = "초기화",
            onClick = onResetClick,
            modifier =
                Modifier
                    .weight(1f)
                    .height(40.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        AppPrimaryButton(
            text = "적용",
            onClick = onApplyClick,
            modifier =
                Modifier
                    .weight(1f)
                    .height(40.dp),
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

private const val REPORT_FILTER_ALPHA = 0.42f
