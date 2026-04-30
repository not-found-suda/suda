package com.ssafy.mobile.feature.conversation.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppOfflineBanner
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.presentation.components.SubtitleList
import com.ssafy.mobile.feature.sign.presentation.SignRecognitionScreen

private const val TAG = "ConversationRoute"
private const val SUBTITLE_WIDTH_FRACTION = 0.95f
private const val SUBTITLE_HEIGHT_FRACTION = 0.4f

data class ConversationUiState(
    val sessionState: SessionState,
    val isOnline: Boolean,
    val messages: List<ChatMessage>,
    val lastGlosses: List<String>,
)

@Composable
fun conversationRoute(
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val lastGlosses by viewModel.lastGlosses.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 세션 활성화 중에는 화면이 꺼지지 않도록 설정합니다.
    DisposableEffect(sessionState) {
        val activity = context.findActivity()
        val window = activity?.window

        if (window == null) {
            Log.e(TAG, "Window not found. Context is ${context.javaClass.simpleName}")
        } else if (sessionState == SessionState.Active) {
            Log.d(TAG, "Adding FLAG_KEEP_SCREEN_ON")
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            Log.d(TAG, "Clearing FLAG_KEEP_SCREEN_ON")
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopSession() }
    }

    val uiState =
        ConversationUiState(
            sessionState = sessionState,
            isOnline = isOnline,
            messages = messages,
            lastGlosses = lastGlosses,
        )

    ConversationScreen(
        uiState = uiState,
        onStartSession = viewModel::startSession,
        onStopSession = viewModel::stopSession,
        onLandmarkFrame = viewModel::onLandmarkFrame,
        modifier = modifier,
    )
}

@Composable
private fun ConversationScreen(
    uiState: ConversationUiState,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onLandmarkFrame: (LandmarkFrameResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                SessionHeader(sessionState = uiState.sessionState)
                AppOfflineBanner(isOnline = uiState.isOnline)
            }
        },
        bottomBar = {
            SessionControls(
                sessionState = uiState.sessionState,
                onStartSession = onStartSession,
                onStopSession = onStopSession,
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
        ) {
            // 카메라 인식 영역 및 자막 오버레이
            SignRecognitionArea(
                sessionState = uiState.sessionState,
                messages = uiState.messages,
                onLandmarkFrame = onLandmarkFrame,
                modifier = Modifier.weight(1.0f),
            )

            // 실시간 인식 결과 영역 (글로스 나열)
            GlossRecognitionArea(lastGlosses = uiState.lastGlosses)
        }
    }
}

@Composable
private fun SignRecognitionArea(
    sessionState: SessionState,
    messages: List<ChatMessage>,
    onLandmarkFrame: (LandmarkFrameResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        SignRecognitionScreen(
            isSessionActive = sessionState == SessionState.Active,
            onLandmarkFrameAvailable = onLandmarkFrame,
            modifier = Modifier.fillMaxSize(),
        )

        // 자막 리스트 오버레이 (하단 40% 영역)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(SUBTITLE_WIDTH_FRACTION)
                    .fillMaxHeight(SUBTITLE_HEIGHT_FRACTION)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
        ) {
            SubtitleList(messages = messages)
        }

        if (sessionState == SessionState.Idle) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "세션을 시작하려면 버튼을 누르세요",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun GlossRecognitionArea(lastGlosses: List<String>) {
    val glossListState = rememberLazyListState()

    LaunchedEffect(lastGlosses.size) {
        if (lastGlosses.isNotEmpty()) {
            glossListState.animateScrollToItem(lastGlosses.lastIndex)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ).padding(12.dp),
    ) {
        if (lastGlosses.isEmpty()) {
            Text(
                text = "수어 인식 대기 중...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(
                state = glossListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(lastGlosses) { gloss ->
                    GlossChip(gloss = gloss)
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(sessionState: SessionState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "SUDA 소통 세션",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            color =
                                if (sessionState ==
                                    SessionState.Active
                                ) {
                                    Color.Green
                                } else {
                                    Color.Gray
                                },
                            shape = CircleShape,
                        ),
            )
            Text(
                text = if (sessionState == SessionState.Active) " LIVE" else " IDLE",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (sessionState == SessionState.Active) Color.Green else Color.Gray,
            )
        }
    }
}

@Composable
private fun SessionControls(
    sessionState: SessionState,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
    ) {
        if (sessionState == SessionState.Idle) {
            AppPrimaryButton(
                text = "대화 시작하기",
                onClick = { onStartSession() },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AppSecondaryButton(
                text = "종료",
                onClick = { onStopSession() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GlossChip(gloss: String) {
    Box(
        modifier =
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ).padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = gloss,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = (currentContext as ContextWrapper).baseContext
    }
    return null
}
