@file:Suppress("TooManyFunctions", "MagicNumber")

package com.ssafy.mobile.feature.mypage.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.R
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import java.util.Locale

@Composable
fun MyPageRoute(
    onNavigateToAppSettings: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onNavigateToAccountEdit: () -> Unit,
    onNavigateToChildSelect: () -> Unit,
    onNavigateToAiModelSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()
    val aiModelState by viewModel.aiModelState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshAiModelState()
    }

    LaunchedEffect(logoutState) {
        when (logoutState) {
            is MyPageLogoutState.Success -> onLogoutSuccess()
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
        onAiModelSettingsClick = onNavigateToAiModelSettings,
        onAppSettingsClick = onNavigateToAppSettings,
        onAccountEditClick = onNavigateToAccountEdit,
        onChildProfileClick = onNavigateToChildSelect,
        onLogoutClick = { showLogoutDialog = true },
        modifier = modifier,
    )

    if (showLogoutDialog) {
        LogoutConfirmDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            },
            onDismiss = { showLogoutDialog = false },
        )
    }
}

@Composable
private fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_mypage_logout),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "로그아웃할까요?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "현재 기기의 로그인 정보만 삭제돼요. 다시 이용하려면 로그인하면 됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppSecondaryButton(
                        text = "취소",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    AppPrimaryButton(
                        text = "로그아웃",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList")
private fun MyPageScreen(
    snackbarHostState: SnackbarHostState,
    aiModelState: AiModelUiState,
    onRefreshModelClick: () -> Unit,
    onAiModelSettingsClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onAccountEditClick: () -> Unit,
    onChildProfileClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                MyPageListGroup(
                    title = "계정",
                    rows =
                        listOf(
                            MyPageMenuSpec(
                                title = "계정 정보",
                                description = "이메일 확인과 이름 수정",
                                iconRes = R.drawable.ic_mypage_account,
                                iconColor = Color(0xFF3F8DF6),
                                onClick = onAccountEditClick,
                            ),
                            MyPageMenuSpec(
                                title = "아이 프로필",
                                description = "프로필 선택과 전환",
                                iconRes = R.drawable.ic_mypage_child_profile,
                                iconColor = Color(0xFF22B8A8),
                                onClick = onChildProfileClick,
                            ),
                        ),
                )
            }
            item {
                MyPageListGroup(
                    title = "앱",
                ) {
                    AiModelManagementRow(
                        state = aiModelState,
                        onRefreshClick = onRefreshModelClick,
                        onClick = onAiModelSettingsClick,
                    )
                    MyPageDivider()
                    MyPageMenuRow(
                        item =
                            MyPageMenuSpec(
                                title = "앱 설정",
                                description = "테마와 사용 환경 설정",
                                iconRes = R.drawable.ic_mypage_settings,
                                iconColor = Color(0xFF8CC63F),
                                onClick = onAppSettingsClick,
                            ),
                    )
                    MyPageDivider()
                    MyPageMenuRow(
                        item =
                            MyPageMenuSpec(
                                title = "로그아웃",
                                description = "현재 기기의 로그인 세션 종료",
                                iconRes = R.drawable.ic_mypage_logout,
                                iconColor = Color(0xFF8B95A1),
                                onClick = onLogoutClick,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyPageListGroup(
    title: String,
    rows: List<MyPageMenuSpec>,
    modifier: Modifier = Modifier,
) {
    MyPageListGroup(title = title, modifier = modifier) {
        rows.forEachIndexed { index, item ->
            MyPageMenuRow(item = item)
            if (index != rows.lastIndex) {
                MyPageDivider()
            }
        }
    }
}

@Composable
private fun MyPageListGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun MyPageDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp, end = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun AiModelManagementRow(
    state: AiModelUiState,
    onRefreshClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        onRefreshClick()
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyPageIcon(
                iconRes = R.drawable.ic_mypage_ai_model,
                color = Color(0xFF6C5CE7),
                modifier = Modifier.padding(start = 20.dp),
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "온디바이스 AI 모델",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AiModelDownloadProgress(
            state = state,
            modifier = Modifier.padding(start = 82.dp, end = 20.dp),
        )
    }
}

@Composable
private fun AiModelDownloadProgress(
    state: AiModelUiState,
    modifier: Modifier = Modifier,
) {
    if (state.status != AiModelDownloadStatus.Downloading) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
                ).joinToString(" · ").ifBlank { "다운로드 정보를 계산하고 있어요." },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MyPageMenuRow(
    item: MyPageMenuSpec,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = item.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MyPageIcon(
            iconRes = item.iconRes,
            color = item.iconColor,
            modifier = Modifier.padding(start = 20.dp),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.badgeText != null) {
            AppBadge(
                text = item.badgeText,
                tone = item.badgeTone,
                modifier = Modifier.padding(end = 20.dp),
            )
        }
    }
}

@Composable
private fun MyPageIcon(
    @DrawableRes iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .size(42.dp),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(23.dp),
            )
        }
    }
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

private data class MyPageMenuSpec(
    val title: String,
    val description: String,
    @param:DrawableRes val iconRes: Int,
    val iconColor: Color,
    val onClick: () -> Unit,
    val badgeText: String? = null,
    val badgeTone: AppBadgeTone = AppBadgeTone.Neutral,
)

private const val BYTES_PER_MIB = 1024.0 * 1024.0
private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0
private const val BYTES_PER_KIB = 1024.0
private const val PERCENT_MAX = 100
private const val SECONDS_PER_MINUTE = 60L
private const val MILLIS_PER_SECOND = 1000L
