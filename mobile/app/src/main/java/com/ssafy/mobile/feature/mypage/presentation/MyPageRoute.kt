@file:Suppress("TooManyFunctions")

package com.ssafy.mobile.feature.mypage.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

@Composable
fun MyPageRoute(
    onLogoutSuccess: () -> Unit,
    onNavigateToAccountEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()
    val aiModelState by viewModel.aiModelState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshAiModelState()
    }

    LaunchedEffect(logoutState) {
        when (logoutState) {
            is MyPageLogoutState.Success -> {
                onLogoutSuccess()
            }
            is MyPageLogoutState.Error -> {
                snackbarHostState.showSnackbar((logoutState as MyPageLogoutState.Error).message)
                viewModel.resetLogoutState()
            }
            else -> Unit
        }
    }

    MyPageScreen(
        snackbarHostState = snackbarHostState,
        aiModelState = aiModelState,
        onRefreshModelClick = viewModel::refreshAiModelState,
        onDownloadModelClick = viewModel::downloadAiModel,
        onCancelDownloadClick = viewModel::cancelAiModelDownload,
        onDeleteModelClick = { showDeleteModelDialog = true },
        onAccountEditClick = onNavigateToAccountEdit,
        onLogoutClick = { showLogoutDialog = true },
        modifier = modifier,
    )

    if (showLogoutDialog) {
        AppDialog(
            title = "로그아웃할까요?",
            message = "현재 기기의 로그인 정보와 선택한 아이 정보가 초기화됩니다.",
            confirmText = "로그아웃",
            dismissText = "취소",
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            },
            onDismiss = { showLogoutDialog = false },
        )
    }

    if (showDeleteModelDialog) {
        AppDialog(
            title = "모델을 삭제할까요?",
            message = "기기에 저장된 온디바이스 AI 모델 파일이 삭제됩니다. 다시 사용하려면 모델을 다시 다운로드해야 합니다.",
            confirmText = "삭제",
            dismissText = "취소",
            onConfirm = {
                showDeleteModelDialog = false
                viewModel.deleteAiModel()
            },
            onDismiss = { showDeleteModelDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun MyPageScreen(
    snackbarHostState: SnackbarHostState,
    aiModelState: AiModelUiState,
    onRefreshModelClick: () -> Unit,
    onDownloadModelClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onDeleteModelClick: () -> Unit,
    onAccountEditClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "마이페이지",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "보호자 계정, 아이 프로필, 앱 설정을 관리합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MyPageMenuItem(
                title = "계정 정보",
                description = "이메일 확인과 이름 수정",
                onClick = onAccountEditClick,
            )
            MyPageMenuItem(
                title = "아이 프로필",
                description = "프로필 선택과 전환",
                onClick = {},
                enabled = false,
                badgeText = "준비 중",
            )
            MyPageMenuItem(
                title = "앱 설정",
                description = "권한, 알림, 학습 환경",
                onClick = {},
                enabled = false,
                badgeText = "준비 중",
            )
            AiModelManagementCard(
                state = aiModelState,
                onRefreshClick = onRefreshModelClick,
                onDownloadClick = onDownloadModelClick,
                onCancelClick = onCancelDownloadClick,
                onDeleteClick = onDeleteModelClick,
            )
            MyPageMenuItem(
                title = "로그아웃",
                description = "로컬 세션 초기화",
                onClick = onLogoutClick,
                badgeText = "위험",
                badgeTone = AppBadgeTone.Error,
                destructive = true,
            )
        }
    }
}

@Composable
private fun AiModelManagementCard(
    state: AiModelUiState,
    onRefreshClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "온디바이스 AI 모델",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppBadge(
                    text = state.status.label(),
                    tone = state.status.badgeTone(),
                )
            }

            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text =
                    "용량: ${formatBytes(state.currentSizeBytes)} / " +
                        formatBytes(state.expectedSizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AiModelDownloadProgress(state = state)
            AiModelActionButtons(
                state = state,
                onRefreshClick = onRefreshClick,
                onDownloadClick = onDownloadClick,
                onCancelClick = onCancelClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.5f
private const val BYTES_PER_MIB = 1024.0 * 1024.0
private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0
private const val BYTES_PER_KIB = 1024.0
private const val PERCENT_MAX = 100
private const val SECONDS_PER_MINUTE = 60L
private const val MILLIS_PER_SECOND = 1000L

@Composable
private fun AiModelDownloadProgress(state: AiModelUiState) {
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
    Text(
        text =
            listOfNotNull(
                state.speedBytesPerSecond?.let { "속도 ${formatSpeed(it)}" },
                state.remainingMillis?.let { "남은 시간 ${formatDuration(it)}" },
            ).joinToString(" · ").ifBlank { "다운로드 정보를 계산하고 있습니다." },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AiModelActionButtons(
    state: AiModelUiState,
    onRefreshClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.status == AiModelDownloadStatus.Downloading) {
            OutlinedButton(
                onClick = onCancelClick,
                modifier = Modifier.weight(1f),
            ) {
                Text("취소")
            }
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f),
            ) {
                Text("다운로드 중")
            }
        } else if (state.canDelete) {
            OutlinedButton(
                onClick = onRefreshClick,
                modifier = Modifier.weight(1f),
            ) {
                Text("모델 파일 확인")
            }
            OutlinedButton(
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
            ) {
                Text("모델 삭제")
            }
        } else {
            OutlinedButton(
                onClick = onRefreshClick,
                modifier = Modifier.weight(1f),
            ) {
                Text("모델 파일 확인")
            }
            Button(
                onClick = onDownloadClick,
                enabled = state.canDownload && !state.isDownloaded,
                modifier = Modifier.weight(1f),
            ) {
                Text(state.downloadButtonText())
            }
        }
    }
}

private fun AiModelUiState.downloadButtonText(): String =
    when {
        isDownloaded -> "준비 완료"
        downloadUrl.isBlank() -> "URL 필요"
        else -> "다운로드"
    }

@Composable
@Suppress("LongParameterList")
private fun MyPageMenuItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badgeText: String? = null,
    badgeTone: AppBadgeTone = AppBadgeTone.Neutral,
    destructive: Boolean = false,
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    val titleColor =
        if (destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    AppCard(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(alpha),
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (badgeText != null) {
                AppBadge(
                    text = badgeText,
                    tone = badgeTone,
                )
            }

            if (enabled) {
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
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

private fun formatBytes(bytes: Long): String =
    when {
        bytes <= 0L -> "-"
        bytes >= BYTES_PER_GIB -> String.format(Locale.US, "%.2fGB", bytes / BYTES_PER_GIB)
        else -> String.format(Locale.US, "%.1fMB", bytes / BYTES_PER_MIB)
    }

private fun formatSpeed(bytesPerSecond: Long): String =
    when {
        bytesPerSecond >= BYTES_PER_GIB ->
            String.format(Locale.US, "%.2fGB/s", bytesPerSecond / BYTES_PER_GIB)
        bytesPerSecond >= BYTES_PER_MIB ->
            String.format(Locale.US, "%.1fMB/s", bytesPerSecond / BYTES_PER_MIB)
        else -> String.format(Locale.US, "%.0fKB/s", bytesPerSecond / BYTES_PER_KIB)
    }

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / MILLIS_PER_SECOND).coerceAtLeast(1L)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (minutes > 0L) {
        "약 ${minutes}분 ${seconds}초"
    } else {
        "약 ${seconds}초"
    }
}
