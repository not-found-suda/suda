@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "UnusedPrivateMember",
)

package com.ssafy.mobile.feature.conversation.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaMascotImage
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.TranslationFeedbackReason
import com.ssafy.mobile.feature.conversation.presentation.components.SubtitleList
import com.ssafy.mobile.feature.sign.presentation.SignRecognitionScreen

private const val TAG = "ConversationRoute"
private const val SUBTITLE_WIDTH_FRACTION = 0.95f
private const val SUBTITLE_HEIGHT_FRACTION = 0.4f
private const val INPUT_LANE_LABEL_ALPHA = 0.14f
private const val CAMERA_SCRIM_ALPHA = 0.54f

data class ConversationUiState(
    val sessionState: SessionState,
    val signInputPhase: SignInputPhase,
    val speechInputPhase: SpeechInputPhase,
    val messages: List<ChatMessage>,
    val translationFeedbackSubmitState: TranslationFeedbackSubmitState,
)

@Composable
fun conversationRoute(
    modifier: Modifier = Modifier,
    onNavigateToLogin: (() -> Unit)? = null,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val signInputPhase by viewModel.signInputPhase.collectAsStateWithLifecycle()
    val speechInputPhase by viewModel.speechInputPhase.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val translationFeedbackSubmitState by
        viewModel.translationFeedbackSubmitState.collectAsStateWithLifecycle()
    val predictionFeedbackToken by viewModel.predictionFeedbackToken.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

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

    LaunchedEffect(predictionFeedbackToken, sessionState) {
        if (sessionState == SessionState.Active && predictionFeedbackToken != 0L) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val uiState =
        ConversationUiState(
            sessionState = sessionState,
            signInputPhase = signInputPhase,
            speechInputPhase = speechInputPhase,
            messages = messages,
            translationFeedbackSubmitState = translationFeedbackSubmitState,
        )

    ConversationScreen(
        uiState = uiState,
        actions =
            ConversationActions(
                onStartSession = viewModel::startSession,
                onStopSession = viewModel::stopSession,
                onLandmarkFrame = viewModel::onLandmarkFrame,
                onFeedbackReasonConfirmed = viewModel::submitTranslationFeedback,
                onFeedbackDismissed = viewModel::clearTranslationFeedbackSubmitState,
                onNavigateToLogin = onNavigateToLogin,
            ),
        modifier = modifier,
    )
}

private data class ConversationActions(
    val onStartSession: () -> Unit,
    val onStopSession: () -> Unit,
    val onLandmarkFrame: (LandmarkFrameResult) -> Unit,
    val onFeedbackReasonConfirmed: (ChatMessage, TranslationFeedbackReason) -> Unit,
    val onFeedbackDismissed: () -> Unit,
    val onNavigateToLogin: (() -> Unit)?,
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
        containerColor = Color.Transparent,
        bottomBar = {
            ConversationSessionControls(
                signInputPhase = uiState.signInputPhase,
                speechInputPhase = uiState.speechInputPhase,
                sessionState = uiState.sessionState,
                onStartSession = actions.onStartSession,
                onStopSession = actions.onStopSession,
                onNavigateToLogin = actions.onNavigateToLogin,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(conversationBackgroundBrush()),
        ) {
            // 카메라 인식 영역 및 자막 오버레이
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, top = 12.dp, end = 12.dp),
            ) {
                ConversationStageCard(
                    sessionState = uiState.sessionState,
                    signInputPhase = uiState.signInputPhase,
                    speechInputPhase = uiState.speechInputPhase,
                    messages = uiState.messages,
                    onLandmarkFrame = actions.onLandmarkFrame,
                    onFeedbackClick = { message ->
                        feedbackTargetMessage = message
                        selectedFeedbackReason = null
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }
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
private fun conversationBackgroundBrush(): Brush =
    Brush.verticalGradient(
        colors =
            listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f),
            ),
    )

@Composable
private fun ConversationStageCard(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
    messages: List<ChatMessage>,
    onLandmarkFrame: (LandmarkFrameResult) -> Unit,
    onFeedbackClick: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppBadge(
                    text = stageBadgeText(sessionState),
                    tone =
                        if (sessionState == SessionState.Active) {
                            AppBadgeTone.Primary
                        } else {
                            AppBadgeTone.Neutral
                        },
                )
                Text(
                    text = conversationMessageSummary(messages.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)),
            ) {
                SignRecognitionArea(
                    sessionState = sessionState,
                    signInputPhase = signInputPhase,
                    speechInputPhase = speechInputPhase,
                    messages = messages,
                    onLandmarkFrame = onLandmarkFrame,
                    onFeedbackClick = onFeedbackClick,
                    showStatusOverlay = false,
                    showSubtitles = sessionState == SessionState.Active || messages.isNotEmpty(),
                    showDebugOverlay = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
                    emptyText = conversationSubtitlePlaceholder(signInputPhase, speechInputPhase),
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
private fun ConversationHeroHeader(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
    onOpenSignDebug: (() -> Unit)?,
    onNavigateToLogin: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isGuestMode = onNavigateToLogin != null

    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppBadge(
                        text = conversationHeroBadgeText(isGuestMode),
                        tone = if (isGuestMode) AppBadgeTone.Secondary else AppBadgeTone.Primary,
                    )
                    AppBadge(
                        text =
                            conversationHeaderLabel(
                                sessionState,
                                signInputPhase,
                                speechInputPhase,
                            ),
                        tone =
                            conversationHeaderTone(
                                sessionState,
                                signInputPhase,
                                speechInputPhase,
                            ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "\uC18C\uD1B5",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text =
                            compactConversationGuideText(
                                sessionState,
                                signInputPhase,
                                speechInputPhase,
                            ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                if (onOpenSignDebug != null || onNavigateToLogin != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        onOpenSignDebug?.let {
                            ConversationActionChip(
                                text = "\uB514\uBC84\uADF8",
                                onClick = it,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        onNavigateToLogin?.let {
                            ConversationActionChip(
                                text = "\uB85C\uADF8\uC778",
                                onClick = it,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.44f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SudaMascotImage(
                        mascot =
                            conversationHeaderMascot(
                                sessionState,
                                signInputPhase,
                                speechInputPhase,
                            ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationActionChip(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ConversationSessionControls(
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
    sessionState: SessionState,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onNavigateToLogin: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppBadge(
                    text = inputStatusHeadline(signInputPhase, speechInputPhase),
                    tone = inputStatusTone(signInputPhase, speechInputPhase),
                )
                Text(
                    text =
                        compactSessionControlText(
                            sessionState,
                            signInputPhase,
                            speechInputPhase,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                onNavigateToLogin?.let { navigateToLogin ->
                    ConversationActionChip(
                        text = "\uB85C\uADF8\uC778",
                        onClick = navigateToLogin,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            if (sessionState == SessionState.Idle) {
                ConversationActionButton(
                    text = "\uC2DC\uC791",
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartSession()
                    },
                    modifier = Modifier.widthIn(min = 104.dp),
                )
            } else {
                ConversationActionButton(
                    text = "\uC885\uB8CC",
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStopSession()
                    },
                    modifier = Modifier.widthIn(min = 104.dp),
                )
            }
        }
    }
}

@Composable
private fun ConversationActionButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .heightIn(min = 46.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun stageBadgeText(sessionState: SessionState): String =
    if (sessionState == SessionState.Active) {
        "\uC2E4\uC2DC\uAC04 \uD654\uBA74"
    } else {
        "\uB300\uAE30 \uD654\uBA74"
    }

private fun conversationMessageSummary(messageCount: Int): String =
    if (messageCount == 0) {
        "\uC544\uC9C1 \uB300\uD654 \uAE30\uB85D\uC774 \uC5C6\uC5B4\uC694"
    } else {
        "\uB204\uC801 \uBA54\uC2DC\uC9C0 ${messageCount}\uAC1C"
    }

private fun conversationHeroBadgeText(isGuestMode: Boolean): String =
    if (isGuestMode) {
        "\uAC8C\uC2A4\uD2B8 \uBAA8\uB4DC"
    } else {
        "\uC2E4\uC2DC\uAC04 \uC18C\uD1B5"
    }

private fun conversationHeaderLabel(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): String =
    when {
        signInputPhase.isWarning() || speechInputPhase.isWarning() -> "\uD655\uC778 \uD544\uC694"
        sessionState == SessionState.Active -> "\uC18C\uD1B5 \uC911"
        else -> "\uC900\uBE44 \uC644\uB8CC"
    }

private fun conversationHeaderTone(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): AppBadgeTone =
    when {
        signInputPhase.isWarning() || speechInputPhase.isWarning() -> AppBadgeTone.Error
        sessionState == SessionState.Active -> AppBadgeTone.Primary
        else -> AppBadgeTone.Neutral
    }

private fun compactConversationGuideText(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): String =
    when {
        signInputPhase.isWarning() || speechInputPhase.isWarning() ->
            "\uC785\uB825 \uC0C1\uD0DC\uB97C \uD55C \uBC88 \uD655\uC778\uD574 \uC8FC\uC138\uC694"
        sessionState == SessionState.Active ->
            "\uAE30\uAE30 \uB0B4 \uC2E4\uC2DC\uAC04 \uC778\uC2DD \uC911"
        else ->
            "\uC218\uC5B4\u00B7\uC74C\uC131 \uC900\uBE44 \uC644\uB8CC"
    }

private fun conversationHeaderMascot(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): SudaMascot =
    when {
        signInputPhase.isWarning() || speechInputPhase.isWarning() -> SudaMascot.IconDifficult
        sessionState == SessionState.Active -> SudaMascot.Microphone
        else -> SudaMascot.IconNormal
    }

private fun inputStatusHeadline(
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): String =
    when {
        signInputPhase.isWarning() || speechInputPhase.isWarning() -> "\uD655\uC778 \uD544\uC694"
        signInputPhase.isWorking() || speechInputPhase.isWorking() -> "\uC2E4\uC2DC\uAC04"
        else -> "\uC900\uBE44 \uC644\uB8CC"
    }

private fun inputStatusTone(
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): AppBadgeTone =
    when {
        signInputPhase.isWarning() || speechInputPhase.isWarning() -> AppBadgeTone.Error
        signInputPhase.isWorking() || speechInputPhase.isWorking() -> AppBadgeTone.Primary
        else -> AppBadgeTone.Neutral
    }

private fun conversationSubtitlePlaceholder(
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): String =
    when {
        signInputPhase == SignInputPhase.Translating ->
            "\uC218\uC5B4 \uBC88\uC5ED \uACB0\uACFC\uB97C \uAE30\uB2E4\uB9AC\uB294 \uC911\uC785\uB2C8\uB2E4."
        speechInputPhase == SpeechInputPhase.Analyzing ->
            "\uC74C\uC131\uC744 \uBD84\uC11D\uD558\uACE0 \uC788\uC5B4\uC694."
        signInputPhase.isWarning() || speechInputPhase.isWarning() ->
            "\uC0C1\uD0DC \uC548\uB0B4 \uB610\uB294 \uC624\uB958 \uBA54\uC2DC\uC9C0\uAC00 " +
                "\uC5EC\uAE30\uC5D0 \uD45C\uC2DC\uB429\uB2C8\uB2E4."
        else ->
            "\uC218\uC5B4 \uB610\uB294 \uC74C\uC131 \uC790\uB9C9\uC774 " +
                "\uC5EC\uAE30\uC5D0 \uD45C\uC2DC\uB429\uB2C8\uB2E4."
    }

private fun compactSessionControlText(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): String =
    when {
        signInputPhase.isWarning() || speechInputPhase.isWarning() ->
            "\uCE74\uBA54\uB77C \uAD6C\uB3C4\uC640 \uB9C8\uC774\uD06C \uC0C1\uD0DC\uB97C \uBCF4\uC815\uD574 \uC8FC\uC138\uC694."
        sessionState == SessionState.Active ->
            "\uC2E4\uC2DC\uAC04 \uC18C\uD1B5\uC774 \uC9C4\uD589 \uC911\uC785\uB2C8\uB2E4."
        else ->
            "\uBC14\uB85C \uC2DC\uC791\uD574 \uC18C\uD1B5\uC744 \uC2DC\uB3C4\uD560 \uC218 \uC788\uC5B4\uC694."
    }

@Composable
private fun SessionHeader(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
    onOpenSignDebug: (() -> Unit)?,
    onNavigateToLogin: (() -> Unit)?,
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
                    text =
                        "기기 내 수어·음성 인식으로 소통해요",
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
                    if (onNavigateToLogin != null) {
                        TextButton(onClick = onNavigateToLogin) {
                            Text(
                                text = "로그인",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
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
