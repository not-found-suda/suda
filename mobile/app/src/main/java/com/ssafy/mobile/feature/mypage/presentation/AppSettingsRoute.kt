@file:Suppress("MagicNumber")

package com.ssafy.mobile.feature.mypage.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.theme.AppThemeMode
import com.ssafy.mobile.feature.mypage.domain.model.TtsSpeakerOption

private data class AppSettingsActions(
    val onThemeModeSelected: (AppThemeMode) -> Unit,
    val onSpeakerSelected: (String) -> Unit,
    val onRetryTtsLoad: () -> Unit,
    val onNavigateBack: () -> Unit,
)

@Composable
fun AppSettingsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.eventMessage) {
        val message = uiState.eventMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearEventMessage()
    }

    AppSettingsScreen(
        themeMode = themeMode,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        actions =
            AppSettingsActions(
                onThemeModeSelected = viewModel::updateThemeMode,
                onSpeakerSelected = viewModel::updateTtsSpeaker,
                onRetryTtsLoad = viewModel::loadTtsSpeakerSettings,
                onNavigateBack = onNavigateBack,
            ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsScreen(
    themeMode: AppThemeMode,
    uiState: AppSettingsUiState,
    snackbarHostState: SnackbarHostState,
    actions: AppSettingsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "앱 설정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = actions.onNavigateBack) {
                        Text(text = "뒤로")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    text = "테마와 퀴즈 음성을 설정해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SettingsSectionHeader(
                    title = "화면 모드",
                    description = "앱 화면 밝기와 테마를 고를 수 있어요.",
                )
            }
            item {
                ThemeModeSettingsGroup(
                    selectedMode = themeMode,
                    onThemeModeSelected = actions.onThemeModeSelected,
                )
            }
            item {
                SettingsSectionHeader(
                    title = "퀴즈 음성",
                    description = "문제를 읽어주는 목소리를 선택해요.",
                )
            }
            item {
                TtsSpeakerSettingsGroup(
                    uiState = uiState,
                    onSpeakerSelected = actions.onSpeakerSelected,
                    onRetryClick = actions.onRetryTtsLoad,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeModeSettingsGroup(
    selectedMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            AppThemeMode.entries.forEachIndexed { index, mode ->
                ThemeModeRow(
                    mode = mode,
                    selected = mode == selectedMode,
                    onClick = { onThemeModeSelected(mode) },
                )
                if (index != AppThemeMode.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsSpeakerSettingsGroup(
    uiState: AppSettingsUiState,
    onSpeakerSelected: (String) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isTtsLoading ->
            SettingsMessageCard(
                message = "목소리 목록을 불러오고 있어요.",
                modifier = modifier,
            )

        uiState.errorMessage != null ->
            SettingsMessageCard(
                message = uiState.errorMessage,
                actionLabel = "다시 시도",
                onActionClick = onRetryClick,
                modifier = modifier,
            )

        uiState.speakers.isEmpty() ->
            SettingsMessageCard(
                message = "선택할 수 있는 목소리가 아직 없어요.",
                modifier = modifier,
            )

        else ->
            TtsSpeakerOptionList(
                speakers = uiState.speakers,
                selectedSpeakerCode = uiState.selectedSpeakerCode,
                isSaving = uiState.isTtsSaving,
                previewingSpeakerCode = uiState.previewingSpeakerCode,
                onSpeakerSelected = onSpeakerSelected,
                modifier = modifier,
            )
    }
}

@Composable
private fun SettingsMessageCard(
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onActionClick != null) {
                TextButton(
                    onClick = onActionClick,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
private fun TtsSpeakerOptionList(
    speakers: List<TtsSpeakerOption>,
    selectedSpeakerCode: String?,
    isSaving: Boolean,
    previewingSpeakerCode: String?,
    onSpeakerSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            SpeakerSelectionSummary(
                selectedSpeakerLabel =
                    speakers
                        .firstOrNull { speaker -> speaker.code == selectedSpeakerCode }
                        ?.label
                        ?: selectedSpeakerCode
                        ?: "아직 선택한 목소리가 없어요.",
                isSaving = isSaving,
                isPreviewing = previewingSpeakerCode != null,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            )
            speakers.forEachIndexed { index, speaker ->
                TtsSpeakerRow(
                    speaker = speaker,
                    selected = speaker.code == selectedSpeakerCode,
                    enabled = !isSaving,
                    onClick = { onSpeakerSelected(speaker.code) },
                )
                if (index != speakers.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakerSelectionSummary(
    selectedSpeakerLabel: String,
    isSaving: Boolean,
    isPreviewing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "현재 목소리",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = selectedSpeakerLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isSaving) {
            Text(
                text = "저장 중이에요...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        } else if (isPreviewing) {
            Text(
                text = "샘플 음성을 재생하고 있어요...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TtsSpeakerRow(
    speaker: TtsSpeakerOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ).clickable(enabled = enabled, onClick = onClick)
                .padding(start = 20.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = speaker.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
        )
    }
}

@Composable
private fun ThemeModeRow(
    mode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ).clickable(onClick = onClick)
                .padding(start = 20.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = mode.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
    }
}
