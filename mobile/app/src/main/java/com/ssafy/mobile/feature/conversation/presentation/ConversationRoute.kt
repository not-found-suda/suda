@file:Suppress("LongMethod", "LongParameterList", "MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.conversation.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.feedback.AppNetworkStatusBanner
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.TranslationFeedbackReason
import com.ssafy.mobile.feature.conversation.domain.model.TranslationMode
import com.ssafy.mobile.feature.conversation.presentation.components.SubtitleList
import com.ssafy.mobile.feature.sign.presentation.SignRecognitionScreen

private const val TAG = "ConversationRoute"
private const val SUBTITLE_WIDTH_FRACTION = 0.95f
private const val SUBTITLE_HEIGHT_FRACTION = 0.4f
private const val INPUT_LANE_LABEL_ALPHA = 0.14f
private const val NOTICE_ENTER_SLIDE_DIVISOR = 5
private const val CAMERA_SCRIM_ALPHA = 0.54f

data class ConversationUiState(
    val sessionState: SessionState,
    val signInputPhase: SignInputPhase,
    val speechInputPhase: SpeechInputPhase,
    val isOnline: Boolean,
    val messages: List<ChatMessage>,
    val lastGlosses: List<String>,
    val translationMode: TranslationMode,
    val translationModeNotice: String?,
    val translationFeedbackSubmitState: TranslationFeedbackSubmitState,
)

@Composable
fun conversationRoute(
    modifier: Modifier = Modifier,
    onOpenSignDebug: (() -> Unit)? = null,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val signInputPhase by viewModel.signInputPhase.collectAsStateWithLifecycle()
    val speechInputPhase by viewModel.speechInputPhase.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val lastGlosses by viewModel.lastGlosses.collectAsStateWithLifecycle()
    val translationMode by viewModel.translationMode.collectAsStateWithLifecycle()
    val translationModeNotice by viewModel.translationModeNotice.collectAsStateWithLifecycle()
    val translationFeedbackSubmitState by
        viewModel.translationFeedbackSubmitState.collectAsStateWithLifecycle()
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
        onDispose {
            val activity = context.findActivity()
            if (activity?.isChangingConfigurations != true) {
                viewModel.stopSession()
            }
        }
    }

    val uiState =
        ConversationUiState(
            sessionState = sessionState,
            signInputPhase = signInputPhase,
            speechInputPhase = speechInputPhase,
            isOnline = isOnline,
            messages = messages,
            lastGlosses = lastGlosses,
            translationMode = translationMode,
            translationModeNotice = translationModeNotice,
            translationFeedbackSubmitState = translationFeedbackSubmitState,
        )

    ConversationScreen(
        uiState = uiState,
        actions =
            ConversationActions(
                onStartSession = viewModel::startSession,
                onStopSession = viewModel::stopSession,
                onTranslationModeSelected = viewModel::updateTranslationMode,
                onLandmarkFrame = viewModel::onLandmarkFrame,
                onFeedbackReasonConfirmed = viewModel::submitTranslationFeedback,
                onFeedbackDismissed = viewModel::clearTranslationFeedbackSubmitState,
                onOpenSignDebug = onOpenSignDebug,
            ),
        modifier = modifier,
    )
}

private data class ConversationActions(
    val onStartSession: () -> Unit,
    val onStopSession: () -> Unit,
    val onTranslationModeSelected: (TranslationMode) -> Unit,
    val onLandmarkFrame: (LandmarkFrameResult) -> Unit,
    val onFeedbackReasonConfirmed: (ChatMessage, TranslationFeedbackReason) -> Unit,
    val onFeedbackDismissed: () -> Unit,
    val onOpenSignDebug: (() -> Unit)?,
)

private data class TranslationFeedbackSheetUiState(
    val message: ChatMessage,
    val selectedReason: TranslationFeedbackReason?,
    val submitState: TranslationFeedbackSubmitState,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(
    uiState: ConversationUiState,
    actions: ConversationActions,
    modifier: Modifier = Modifier,
) {
    var feedbackTargetMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedFeedbackReason by remember { mutableStateOf<TranslationFeedbackReason?>(null) }

    fun closeFeedbackSheet() {
        feedbackTargetMessage = null
        selectedFeedbackReason = null
        actions.onFeedbackDismissed()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
            ) {
                SessionHeader(
                    sessionState = uiState.sessionState,
                    signInputPhase = uiState.signInputPhase,
                    speechInputPhase = uiState.speechInputPhase,
                    translationMode = uiState.translationMode,
                    onTranslationModeSelected = actions.onTranslationModeSelected,
                    onOpenSignDebug = actions.onOpenSignDebug,
                )
                AppNetworkStatusBanner(isOnline = uiState.isOnline)
                TranslationModeNotice(text = uiState.translationModeNotice)
            }
        },
        bottomBar = {
            SessionControls(
                sessionState = uiState.sessionState,
                onStartSession = actions.onStartSession,
                onStopSession = actions.onStopSession,
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            // 카메라 인식 영역 및 자막 오버레이
            SignRecognitionArea(
                sessionState = uiState.sessionState,
                signInputPhase = uiState.signInputPhase,
                speechInputPhase = uiState.speechInputPhase,
                messages = uiState.messages,
                onLandmarkFrame = actions.onLandmarkFrame,
                onFeedbackClick = { message ->
                    feedbackTargetMessage = message
                    selectedFeedbackReason = null
                },
                showStatusOverlay = false,
                showSubtitles =
                    uiState.sessionState == SessionState.Active ||
                        uiState.messages.isNotEmpty(),
                showDebugOverlay = false,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )

            ParallelInputStatusCard(
                signInputPhase = uiState.signInputPhase,
                speechInputPhase = uiState.speechInputPhase,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    TranslationFeedbackSheetHost(
        uiState =
            feedbackTargetMessage?.let { message ->
                TranslationFeedbackSheetUiState(
                    message = message,
                    selectedReason = selectedFeedbackReason,
                    submitState = uiState.translationFeedbackSubmitState,
                )
            },
        actions = actions,
        onReasonSelected = { selectedFeedbackReason = it },
        onDismiss = { closeFeedbackSheet() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationFeedbackSheetHost(
    uiState: TranslationFeedbackSheetUiState?,
    actions: ConversationActions,
    onReasonSelected: (TranslationFeedbackReason) -> Unit,
    onDismiss: () -> Unit,
) {
    if (uiState == null) return

    val feedbackSheetState = rememberModalBottomSheetState()
    val isFeedbackSubmitting =
        uiState.submitState == TranslationFeedbackSubmitState.Submitting

    ModalBottomSheet(
        onDismissRequest = {
            if (!isFeedbackSubmitting) {
                onDismiss()
            }
        },
        sheetState = feedbackSheetState,
    ) {
        TranslationFeedbackSheet(
            uiState = uiState,
            onReasonSelected = onReasonSelected,
            onConfirmClick = actions.onFeedbackReasonConfirmed,
            onCancelClick = onDismiss,
        )
    }
}

@Composable
private fun SignRecognitionArea(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
    messages: List<ChatMessage>,
    onLandmarkFrame: (LandmarkFrameResult) -> Unit,
    onFeedbackClick: (ChatMessage) -> Unit,
    showStatusOverlay: Boolean = true,
    showSubtitles: Boolean = true,
    showDebugOverlay: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        SignRecognitionScreen(
            isSessionActive = sessionState == SessionState.Active,
            showDebugOverlay = showDebugOverlay,
            onLandmarkFrameAvailable = onLandmarkFrame,
            modifier = Modifier.fillMaxSize(),
        )

        if (showSubtitles) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(SUBTITLE_HEIGHT_FRACTION)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = CAMERA_SCRIM_ALPHA)),
            )
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
                    emptyText = subtitlePlaceholder(signInputPhase, speechInputPhase),
                    onFeedbackClick = onFeedbackClick,
                )
            }
        }

        if (showStatusOverlay && sessionState == SessionState.Active) {
            ParallelInputStatusCard(
                signInputPhase = signInputPhase,
                speechInputPhase = speechInputPhase,
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
                Surface(
                    color = Color.Black.copy(alpha = CAMERA_SCRIM_ALPHA),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "대화 시작을 누르면 자막이 여기에 표시됩니다.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationFeedbackSheet(
    uiState: TranslationFeedbackSheetUiState,
    onReasonSelected: (TranslationFeedbackReason) -> Unit,
    onConfirmClick: (ChatMessage, TranslationFeedbackReason) -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSubmitting = uiState.submitState == TranslationFeedbackSubmitState.Submitting
    val isSuccess = uiState.submitState == TranslationFeedbackSubmitState.Success

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "번역 신고",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = uiState.message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        TranslationFeedbackReason.entries.forEach { reason ->
            TranslationFeedbackReasonRow(
                reason = reason,
                selected = reason == uiState.selectedReason,
                enabled = !isSubmitting && !isSuccess,
                onClick = { onReasonSelected(reason) },
            )
        }

        TranslationFeedbackStatusText(submitState = uiState.submitState)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancelClick,
                enabled = !isSubmitting,
            ) {
                Text(text = "취소")
            }
            TextButton(
                onClick = {
                    when (uiState.submitState) {
                        TranslationFeedbackSubmitState.Idle ->
                            uiState.selectedReason?.let { reason ->
                                onConfirmClick(uiState.message, reason)
                            }
                        is TranslationFeedbackSubmitState.Error ->
                            uiState.selectedReason?.let { reason ->
                                onConfirmClick(uiState.message, reason)
                            }
                        TranslationFeedbackSubmitState.Success -> onCancelClick()
                        TranslationFeedbackSubmitState.Submitting -> Unit
                    }
                },
                enabled = uiState.selectedReason != null && !isSubmitting,
            ) {
                Text(text = uiState.submitState.actionText())
            }
        }
    }
}

@Composable
private fun TranslationFeedbackStatusText(
    submitState: TranslationFeedbackSubmitState,
    modifier: Modifier = Modifier,
) {
    val text =
        when (submitState) {
            TranslationFeedbackSubmitState.Idle -> null
            TranslationFeedbackSubmitState.Submitting -> "피드백을 제출하는 중입니다."
            TranslationFeedbackSubmitState.Success -> "피드백이 접수되었습니다."
            is TranslationFeedbackSubmitState.Error -> submitState.message
        }

    if (text != null) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color =
                when (submitState) {
                    is TranslationFeedbackSubmitState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

private fun TranslationFeedbackSubmitState.actionText(): String =
    when (this) {
        TranslationFeedbackSubmitState.Idle -> "제출"
        TranslationFeedbackSubmitState.Submitting -> "제출 중"
        TranslationFeedbackSubmitState.Success -> "확인"
        is TranslationFeedbackSubmitState.Error -> "다시 제출"
    }

@Composable
private fun TranslationFeedbackReasonRow(
    reason: TranslationFeedbackReason,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                ),
        shape = RoundedCornerShape(12.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = reason.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = reason.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionHeader(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
    translationMode: TranslationMode,
    onTranslationModeSelected: (TranslationMode) -> Unit,
    onOpenSignDebug: (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "소통",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = translationMode.summaryLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (onOpenSignDebug != null) {
                        Surface(
                            modifier =
                                Modifier
                                    .sizeIn(minWidth = 56.dp, minHeight = 30.dp)
                                    .clickable(onClick = onOpenSignDebug),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "디버그",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    AppBadge(
                        text =
                            headerLabel(
                                sessionState = sessionState,
                                signInputPhase = signInputPhase,
                                speechInputPhase = speechInputPhase,
                            ),
                        tone =
                            if (signInputPhase.isWarning() || speechInputPhase.isWarning()) {
                                AppBadgeTone.Error
                            } else if (sessionState == SessionState.Active) {
                                AppBadgeTone.Primary
                            } else {
                                AppBadgeTone.Neutral
                            },
                    )
                }

                TranslationModeToggleButton(
                    selectedMode = translationMode,
                    onModeSelected = onTranslationModeSelected,
                )
            }
        }
    }
}

@Composable
private fun ParallelInputStatusCard(
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputStatusPill(
            label = "수어",
            title = signInputPhase.title(),
            isActive = signInputPhase.isWorking(),
            isWarning = signInputPhase.isWarning(),
            modifier = Modifier.weight(1f),
        )
        InputStatusPill(
            label = "음성",
            title = speechInputPhase.title(),
            isActive = speechInputPhase.isWorking(),
            isWarning = speechInputPhase.isWarning(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InputStatusPill(
    label: String,
    title: String,
    isActive: Boolean,
    isWarning: Boolean,
    modifier: Modifier = Modifier,
) {
    val targetColor =
        when {
            isWarning -> MaterialTheme.colorScheme.error
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }
    val statusColor by animateColorAsState(
        targetValue = targetColor,
        label = "InputStatusColor",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color =
            if (isActive || isWarning) {
                statusColor.copy(alpha = INPUT_LANE_LABEL_ALPHA)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            },
        border =
            BorderStroke(
                width = 1.dp,
                color = statusColor.copy(alpha = if (isActive || isWarning) 0.32f else 0.16f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = statusColor,
                content = {},
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                )
                AnimatedContent(
                    targetState = title,
                    label = "InputStatusText",
                ) { animatedTitle ->
                    Text(
                        text = animatedTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationModeToggleButton(
    selectedMode: TranslationMode,
    onModeSelected: (TranslationMode) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .clickable { onModeSelected(selectedMode.nextMode()) },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "인식",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            AnimatedContent(
                targetState = selectedMode.label(),
                label = "TranslationModeToggleText",
            ) { modeLabel ->
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TranslationModeNotice(text: String?) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn() + slideInVertically { it / NOTICE_ENTER_SLIDE_DIVISOR },
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = text.orEmpty(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun TranslationMode.label(): String =
    when (this) {
        TranslationMode.AUTO -> "자동"
        TranslationMode.SERVER -> "서버"
        TranslationMode.ON_DEVICE -> "기기"
    }

private fun TranslationMode.nextMode(): TranslationMode =
    when (this) {
        TranslationMode.AUTO -> TranslationMode.SERVER
        TranslationMode.SERVER -> TranslationMode.ON_DEVICE
        TranslationMode.ON_DEVICE -> TranslationMode.AUTO
    }

private fun TranslationMode.summaryLabel(): String =
    when (this) {
        TranslationMode.AUTO -> "온라인은 서버, 오프라인은 기기 인식"
        TranslationMode.SERVER -> "서버 음성 인식 사용"
        TranslationMode.ON_DEVICE -> "기기 내 음성 인식 사용"
    }

private fun headerLabel(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): String =
    if (sessionState == SessionState.Active) {
        if (signInputPhase.isWarning() || speechInputPhase.isWarning()) {
            "확인 필요"
        } else {
            "소통 중"
        }
    } else {
        "대기"
    }

private fun SignInputPhase.title(): String =
    when (this) {
        SignInputPhase.Idle -> "대기"
        SignInputPhase.Preparing -> "준비"
        SignInputPhase.Recognizing -> "인식 중"
        SignInputPhase.NoHandsDetected -> "손 확인"
        SignInputPhase.Collecting -> "수집 중"
        SignInputPhase.Translating -> "번역 중"
        SignInputPhase.Fallback -> "기기 처리"
        SignInputPhase.Error -> "확인 필요"
    }

private fun SpeechInputPhase.title(): String =
    when (this) {
        SpeechInputPhase.Idle -> "대기"
        SpeechInputPhase.Listening -> "듣는 중"
        SpeechInputPhase.Analyzing -> "분석 중"
        SpeechInputPhase.Unrecognized -> "소리 감지"
        SpeechInputPhase.Fallback -> "기기 인식"
        SpeechInputPhase.Error -> "확인 필요"
    }

private fun subtitlePlaceholder(
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): String =
    when {
        signInputPhase == SignInputPhase.Translating -> "수어 번역 결과를 기다리는 중입니다."
        speechInputPhase == SpeechInputPhase.Analyzing -> "음성을 분석하고 있어요."
        signInputPhase.isWarning() || speechInputPhase.isWarning() ->
            "상태 안내 또는 오류 메시지가 여기에 표시됩니다."
        else -> "수어 또는 음성 자막이 여기에 표시됩니다."
    }

private fun SignInputPhase.isWarning(): Boolean =
    this == SignInputPhase.NoHandsDetected ||
        this == SignInputPhase.Fallback ||
        this == SignInputPhase.Error

private fun SpeechInputPhase.isWarning(): Boolean =
    this == SpeechInputPhase.Fallback || this == SpeechInputPhase.Error

private fun SignInputPhase.isWorking(): Boolean = this != SignInputPhase.Idle

private fun SpeechInputPhase.isWorking(): Boolean = this != SpeechInputPhase.Idle

@Composable
private fun SessionControls(
    sessionState: SessionState,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        modifier =
            Modifier
                .fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (sessionState == SessionState.Idle) {
                SessionActionButton(
                    text = "시작",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartSession()
                    },
                )
            } else {
                SessionActionButton(
                    text = "끝내기",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStopSession()
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionActionButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .widthIn(min = 96.dp, max = 140.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
