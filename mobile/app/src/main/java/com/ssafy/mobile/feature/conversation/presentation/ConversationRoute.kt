@file:Suppress("MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.conversation.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.feedback.AppNetworkStatusBanner
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.TranslationMode
import com.ssafy.mobile.feature.conversation.presentation.components.SubtitleList
import com.ssafy.mobile.feature.sign.presentation.SignRecognitionScreen

private const val TAG = "ConversationRoute"
private const val SUBTITLE_WIDTH_FRACTION = 0.95f
private const val SUBTITLE_HEIGHT_FRACTION = 0.4f
private const val PHASE_CARD_ALPHA = 0.88f

data class ConversationUiState(
    val sessionState: SessionState,
    val conversationPhase: ConversationPhase,
    val isOnline: Boolean,
    val messages: List<ChatMessage>,
    val lastGlosses: List<String>,
    val translationMode: TranslationMode,
    val translationModeNotice: String?,
)

@Composable
fun conversationRoute(
    modifier: Modifier = Modifier,
    onOpenSignDebug: (() -> Unit)? = null,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val conversationPhase by viewModel.conversationPhase.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val lastGlosses by viewModel.lastGlosses.collectAsStateWithLifecycle()
    val translationMode by viewModel.translationMode.collectAsStateWithLifecycle()
    val translationModeNotice by viewModel.translationModeNotice.collectAsStateWithLifecycle()
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
            conversationPhase = conversationPhase,
            isOnline = isOnline,
            messages = messages,
            lastGlosses = lastGlosses,
            translationMode = translationMode,
            translationModeNotice = translationModeNotice,
        )

    ConversationScreen(
        uiState = uiState,
        onStartSession = viewModel::startSession,
        onStopSession = viewModel::stopSession,
        onTranslationModeSelected = viewModel::updateTranslationMode,
        onLandmarkFrame = viewModel::onLandmarkFrame,
        onOpenSignDebug = onOpenSignDebug,
        modifier = modifier,
    )
}

@Composable
private fun ConversationScreen(
    uiState: ConversationUiState,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onTranslationModeSelected: (TranslationMode) -> Unit,
    onLandmarkFrame: (LandmarkFrameResult) -> Unit,
    onOpenSignDebug: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                SessionHeader(
                    sessionState = uiState.sessionState,
                    conversationPhase = uiState.conversationPhase,
                    translationMode = uiState.translationMode,
                    onTranslationModeSelected = onTranslationModeSelected,
                    onOpenSignDebug = onOpenSignDebug,
                )
                AppNetworkStatusBanner(isOnline = uiState.isOnline)
                TranslationModeNotice(text = uiState.translationModeNotice)
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
                conversationPhase = uiState.conversationPhase,
                messages = uiState.messages,
                onLandmarkFrame = onLandmarkFrame,
                modifier = Modifier.weight(1.0f),
            )

            // 실시간 인식 결과 영역 (글로스 나열)
            GlossRecognitionArea(
                lastGlosses = uiState.lastGlosses,
                conversationPhase = uiState.conversationPhase,
            )
        }
    }
}

@Composable
private fun SignRecognitionArea(
    sessionState: SessionState,
    conversationPhase: ConversationPhase,
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
            SubtitleList(
                messages = messages,
                emptyText = conversationPhase.subtitlePlaceholder(),
            )
        }

        if (sessionState == SessionState.Active) {
            ConversationPhaseCard(
                phase = conversationPhase,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            )
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
private fun GlossRecognitionArea(
    lastGlosses: List<String>,
    conversationPhase: ConversationPhase,
) {
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
                text = conversationPhase.glossPlaceholder(),
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
private fun SessionHeader(
    sessionState: SessionState,
    conversationPhase: ConversationPhase,
    translationMode: TranslationMode,
    onTranslationModeSelected: (TranslationMode) -> Unit,
    onOpenSignDebug: (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "SUDA 소통 세션",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onOpenSignDebug != null) {
                    Box(
                        modifier =
                            Modifier
                                .sizeIn(minWidth = 72.dp, minHeight = 40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape,
                                ).clickable(onClick = onOpenSignDebug)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "디버그",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
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
                    text = conversationPhase.headerLabel(sessionState),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sessionState == SessionState.Active) Color.Green else Color.Gray,
                )
            }
        }

        TranslationModeSelector(
            selectedMode = translationMode,
            onModeSelected = onTranslationModeSelected,
        )
    }
}

@Composable
private fun ConversationPhaseCard(
    phase: ConversationPhase,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = phase.containerColor(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = phase.title(),
                style = MaterialTheme.typography.titleSmall,
                color = phase.contentColor(),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = phase.description(),
                style = MaterialTheme.typography.bodySmall,
                color = phase.contentColor(),
            )
        }
    }
}

@Composable
private fun TranslationModeSelector(
    selectedMode: TranslationMode,
    onModeSelected: (TranslationMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TranslationMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onModeSelected(mode) },
                shape = RoundedCornerShape(999.dp),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                border =
                    BorderStroke(
                        width = 1.dp,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                    ),
            ) {
                Text(
                    text = mode.label(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TranslationModeNotice(text: String?) {
    if (text.isNullOrBlank()) return

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun TranslationMode.label(): String =
    when (this) {
        TranslationMode.AUTO -> "자동"
        TranslationMode.SERVER -> "서버"
        TranslationMode.ON_DEVICE -> "기기"
    }

private fun ConversationPhase.headerLabel(sessionState: SessionState): String =
    if (sessionState == SessionState.Active) {
        " ${title()}"
    } else {
        " IDLE"
    }

private fun ConversationPhase.title(): String =
    when (this) {
        ConversationPhase.Idle -> "대기 중"
        ConversationPhase.Preparing -> "준비 중"
        ConversationPhase.RecognizingSign -> "수어 인식 중"
        ConversationPhase.NoHandsDetected -> "손 인식 대기"
        ConversationPhase.CollectingSigns -> "문장 수집 중"
        ConversationPhase.Translating -> "번역 중"
        ConversationPhase.Speaking -> "음성 재생 중"
        ConversationPhase.ListeningSpeech -> "음성 듣는 중"
        ConversationPhase.AnalyzingSpeech -> "음성 분석 중"
        ConversationPhase.Fallback -> "기기 처리 전환"
        ConversationPhase.Error -> "확인 필요"
    }

private fun ConversationPhase.description(): String =
    when (this) {
        ConversationPhase.Idle -> "대화를 시작하면 카메라와 음성 인식이 함께 동작합니다."
        ConversationPhase.Preparing -> "카메라와 수어 인식 모델을 준비하고 있어요."
        ConversationPhase.RecognizingSign -> "화면 안에서 수어를 보여 주세요."
        ConversationPhase.NoHandsDetected -> "손이 화면에 잘 보이도록 위치를 맞춰 주세요."
        ConversationPhase.CollectingSigns -> "인식된 수어를 문장으로 묶고 있어요."
        ConversationPhase.Translating -> "수어 문장을 자연스러운 말로 바꾸는 중입니다."
        ConversationPhase.Speaking -> "번역된 문장을 음성으로 재생하고 있어요."
        ConversationPhase.ListeningSpeech -> "상대방의 말을 듣고 자막으로 옮길 준비가 됐어요."
        ConversationPhase.AnalyzingSpeech -> "방금 들은 음성을 분석하고 있어요."
        ConversationPhase.Fallback -> "서버 대신 기기 내 처리로 이어가고 있어요."
        ConversationPhase.Error -> "일시적으로 처리하지 못했습니다. 세션을 다시 시도해 주세요."
    }

private fun ConversationPhase.subtitlePlaceholder(): String =
    when (this) {
        ConversationPhase.Idle -> "대화를 시작하면 자막이 여기에 표시됩니다."
        ConversationPhase.Preparing -> "소통 세션을 준비하고 있어요."
        ConversationPhase.RecognizingSign,
        ConversationPhase.NoHandsDetected,
        -> "수어 또는 음성 자막이 여기에 표시됩니다."
        ConversationPhase.CollectingSigns -> "수어 문장을 모으고 있어요."
        ConversationPhase.Translating -> "번역 결과를 기다리는 중입니다."
        ConversationPhase.Speaking -> "번역 음성을 재생하고 있어요."
        ConversationPhase.ListeningSpeech -> "상대방의 말을 듣고 있어요."
        ConversationPhase.AnalyzingSpeech -> "음성을 분석하고 있어요."
        ConversationPhase.Fallback -> "기기 내 처리로 자막을 이어가고 있어요."
        ConversationPhase.Error -> "상태 안내 또는 오류 메시지가 여기에 표시됩니다."
    }

private fun ConversationPhase.glossPlaceholder(): String =
    when (this) {
        ConversationPhase.Idle -> "세션 시작 전입니다."
        ConversationPhase.Preparing -> "수어 인식 준비 중..."
        ConversationPhase.NoHandsDetected -> "손을 화면 안에 보여 주세요."
        ConversationPhase.Translating -> "번역 중..."
        ConversationPhase.Speaking -> "음성 재생 중..."
        ConversationPhase.Fallback -> "기기 내 처리로 전환됨"
        ConversationPhase.Error -> "세션 상태를 확인해 주세요."
        else -> "수어 인식 대기 중..."
    }

@Composable
private fun ConversationPhase.containerColor(): Color =
    when (this) {
        ConversationPhase.Error -> MaterialTheme.colorScheme.errorContainer
        ConversationPhase.Fallback -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface.copy(alpha = PHASE_CARD_ALPHA)
    }

@Composable
private fun ConversationPhase.contentColor(): Color =
    when (this) {
        ConversationPhase.Error -> MaterialTheme.colorScheme.onErrorContainer
        ConversationPhase.Fallback -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
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
