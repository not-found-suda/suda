@file:Suppress("MagicNumber")

package com.ssafy.mobile.feature.mypage.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppDialog
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun AiModelSettingsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val aiModelState by viewModel.aiModelState.collectAsStateWithLifecycle()
    var isDefaultEditMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshAiModelState()
    }

    AiModelSettingsScreen(
        state = aiModelState,
        isDefaultEditMode = isDefaultEditMode,
        snackbarHostState = snackbarHostState,
        onToggleDefaultEditMode = {
            isDefaultEditMode = !isDefaultEditMode
        },
        onSelectDefaultModel = {
            isDefaultEditMode = false
            coroutineScope.launch {
                snackbarHostState.showSnackbar("기본 모델로 설정했어요.")
            }
        },
        onDownloadClick = viewModel::downloadAiModel,
        onCancelClick = viewModel::cancelAiModelDownload,
        onDeleteClick = { showDeleteDialog = true },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )

    if (showDeleteDialog) {
        AppDialog(
            title = "AI 모델을 삭제할까요?",
            message = "기기에 저장된 온디바이스 AI 모델 파일을 삭제합니다. 다시 사용하려면 모델을 다시 다운로드해야 해요.",
            confirmText = "삭제",
            dismissText = "취소",
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteAiModel()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun AiModelSettingsScreen(
    state: AiModelUiState,
    isDefaultEditMode: Boolean,
    snackbarHostState: SnackbarHostState,
    onToggleDefaultEditMode: () -> Unit,
    onSelectDefaultModel: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "온디바이스 모델",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = onToggleDefaultEditMode) {
                        Text(if (isDefaultEditMode) "완료" else "기본값 설정")
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
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AiModelListItem(
                    state = state,
                    isDefaultEditMode = isDefaultEditMode,
                    onSelectDefaultModel = onSelectDefaultModel,
                    onDownloadClick = onDownloadClick,
                    onCancelClick = onCancelClick,
                    onDeleteClick = onDeleteClick,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun AiModelListItem(
    state: AiModelUiState,
    isDefaultEditMode: Boolean,
    onSelectDefaultModel: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = state.modelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        AppBadge(text = "기본값", tone = AppBadgeTone.Primary)
                    }
                }
                if (isDefaultEditMode) {
                    RadioButton(
                        selected = true,
                        onClick = onSelectDefaultModel,
                    )
                } else {
                    AiModelRowActionButton(
                        state = state,
                        onDownloadClick = onDownloadClick,
                        onCancelClick = onCancelClick,
                        onDeleteClick = onDeleteClick,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBadge(text = state.status.label(), tone = state.status.badgeTone())
                Text(
                    text =
                        "${formatModelBytes(state.currentSizeBytes)} / " +
                            formatModelBytes(state.expectedSizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AiModelProgress(state = state)
        }
    }
}

@Composable
private fun AiModelRowActionButton(
    state: AiModelUiState,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    when {
        state.status == AiModelDownloadStatus.Downloading -> {
            IconButton(onClick = onCancelClick) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        state.canDelete -> {
            IconButton(onClick = onDeleteClick) {
                Text(
                    text = "삭제",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        else -> {
            IconButton(
                onClick = onDownloadClick,
                enabled = state.canDownload,
            ) {
                Text(
                    text = "↓",
                    style = MaterialTheme.typography.titleLarge,
                    color =
                        if (state.canDownload) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AiModelProgress(state: AiModelUiState) {
    if (state.status != AiModelDownloadStatus.Downloading) return

    val progress = state.progressPercent?.coerceIn(0, PERCENT_MAX)
    if (progress == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { progress / PERCENT_MAX.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun AiModelDownloadStatus.label(): String =
    when (this) {
        AiModelDownloadStatus.Ready -> "준비 완료"
        AiModelDownloadStatus.Missing -> "미설치"
        AiModelDownloadStatus.Downloading -> "다운로드 중"
        AiModelDownloadStatus.Success -> "완료"
        AiModelDownloadStatus.Canceled -> "취소됨"
        AiModelDownloadStatus.Failed -> "실패"
    }

private fun AiModelDownloadStatus.badgeTone(): AppBadgeTone =
    when (this) {
        AiModelDownloadStatus.Ready,
        AiModelDownloadStatus.Success,
        -> AppBadgeTone.Success
        AiModelDownloadStatus.Downloading -> AppBadgeTone.Warning
        AiModelDownloadStatus.Canceled,
        AiModelDownloadStatus.Missing,
        -> AppBadgeTone.Neutral
        AiModelDownloadStatus.Failed -> AppBadgeTone.Error
    }

private fun formatModelBytes(bytes: Long): String =
    when {
        bytes <= 0L -> "-"
        bytes >= BYTES_PER_GIB -> String.format(Locale.US, "%.2fGB", bytes / BYTES_PER_GIB)
        else -> String.format(Locale.US, "%.1fMB", bytes / BYTES_PER_MIB)
    }

private const val BYTES_PER_MIB = 1024.0 * 1024.0
private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0
private const val PERCENT_MAX = 100
