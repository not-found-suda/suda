@file:Suppress("LongMethod", "LongParameterList", "MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.conversation.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
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
private const val PHASE_CARD_ALPHA = 0.88f
private const val INPUT_LANE_LABEL_ALPHA = 0.14f
private const val CAMERA_PREVIEW_ASPECT_RATIO = 16f / 9f

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
    var isCameraFullscreen by rememberSaveable { mutableStateOf(false) }

    fun closeFeedbackSheet() {
        feedbackTargetMessage = null
        selectedFeedbackReason = null
        actions.onFeedbackDismissed()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
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
            if (isCameraFullscreen) {
                CameraFullscreenPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(CAMERA_PREVIEW_ASPECT_RATIO),
                )
            } else {
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
                    onFullscreenClick =
                        {
                            isCameraFullscreen = true
                        }.takeIf { uiState.sessionState == SessionState.Active },
                    showStatusOverlay = false,
                    showSubtitles = false,
                    showDebugOverlay = false,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(CAMERA_PREVIEW_ASPECT_RATIO),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background),
            ) {
                ParallelInputStatusCard(
                    signInputPhase = uiState.signInputPhase,
                    speechInputPhase = uiState.speechInputPhase,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                )
                GlossRecognitionArea(
                    lastGlosses = uiState.lastGlosses,
                    signInputPhase = uiState.signInputPhase,
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

    if (isCameraFullscreen) {
        CameraFullscreenDialog(
            uiState = uiState,
            actions = actions,
            onDismiss = {
                isCameraFullscreen = false
            },
        )
    }
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
private fun CameraFullscreenPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "전체 화면으로 표시 중",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
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
    onFullscreenClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
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

        if (onFullscreenClick != null) {
            CameraOverlayButton(
                text = "전체",
                onClick = onFullscreenClick,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
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
private fun CameraFullscreenDialog(
    uiState: ConversationUiState,
    actions: ConversationActions,
    onDismiss: () -> Unit,
) {
    ForceLandscapeOrientationEffect()

    LaunchedEffect(uiState.sessionState) {
        if (uiState.sessionState != SessionState.Active) {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    SignRecognitionArea(
                        sessionState = uiState.sessionState,
                        signInputPhase = uiState.signInputPhase,
                        speechInputPhase = uiState.speechInputPhase,
                        messages = uiState.messages,
                        onLandmarkFrame = actions.onLandmarkFrame,
                        onFeedbackClick = {},
                        showStatusOverlay = false,
                        showSubtitles = false,
                        showDebugOverlay = false,
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(CAMERA_PREVIEW_ASPECT_RATIO),
                    )
                }
                FullscreenStatusPanel(
                    uiState = uiState,
                    onStopSession = {
                        actions.onStopSession()
                        onDismiss()
                    },
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(320.dp),
                )
            }
        }
    }
}

@Composable
private fun ForceLandscapeOrientationEffect() {
    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { }
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            onDispose {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
}

@Composable
private fun FullscreenStatusPanel(
    uiState: ConversationUiState,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "실시간 수어",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            InputLaneStatusRow(
                label = "수어",
                title = uiState.signInputPhase.title(),
                description = uiState.signInputPhase.description(),
                isWarning = uiState.signInputPhase.isWarning(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                text = "인식 결과",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (uiState.lastGlosses.isEmpty()) {
                Text(
                    text = uiState.signInputPhase.glossPlaceholder(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.lastGlosses) { gloss ->
                        GlossChip(gloss = gloss)
                    }
                }
            }
            Box(modifier = Modifier.weight(1f))
            AppSecondaryButton(
                text = "종료",
                onClick = onStopSession,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CameraOverlayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
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
private fun GlossRecognitionArea(
    lastGlosses: List<String>,
    signInputPhase: SignInputPhase,
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
                text = signInputPhase.glossPlaceholder(),
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
                    text =
                        headerLabel(
                            sessionState = sessionState,
                            signInputPhase = signInputPhase,
                            speechInputPhase = speechInputPhase,
                        ),
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
private fun ParallelInputStatusCard(
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = PHASE_CARD_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InputLaneStatusRow(
                label = "수어",
                title = signInputPhase.title(),
                description = signInputPhase.description(),
                isWarning = signInputPhase.isWarning(),
            )
            InputLaneStatusRow(
                label = "음성",
                title = speechInputPhase.title(),
                description = speechInputPhase.description(),
                isWarning = speechInputPhase.isWarning(),
            )
        }
    }
}

@Composable
private fun InputLaneStatusRow(
    label: String,
    title: String,
    description: String,
    isWarning: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusColor =
        if (isWarning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = statusColor.copy(alpha = INPUT_LANE_LABEL_ALPHA),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun headerLabel(
    sessionState: SessionState,
    signInputPhase: SignInputPhase,
    speechInputPhase: SpeechInputPhase,
): String =
    if (sessionState == SessionState.Active) {
        if (signInputPhase.isWarning() || speechInputPhase.isWarning()) {
            " 확인 필요"
        } else {
            " 병렬 소통 중"
        }
    } else {
        " IDLE"
    }

private fun SignInputPhase.title(): String =
    when (this) {
        SignInputPhase.Idle -> "대기 중"
        SignInputPhase.Preparing -> "준비 중"
        SignInputPhase.Recognizing -> "수어 인식 중"
        SignInputPhase.NoHandsDetected -> "손 인식 대기"
        SignInputPhase.Collecting -> "문장 수집 중"
        SignInputPhase.Translating -> "번역 중"
        SignInputPhase.Fallback -> "기기 처리 전환"
        SignInputPhase.Error -> "확인 필요"
    }

private fun SignInputPhase.description(): String =
    when (this) {
        SignInputPhase.Idle -> "대화를 시작하면 카메라 인식이 준비됩니다."
        SignInputPhase.Preparing -> "카메라와 수어 인식 모델을 준비하고 있어요."
        SignInputPhase.Recognizing -> "화면 안에서 수어를 보여 주세요."
        SignInputPhase.NoHandsDetected -> "손이 화면에 잘 보이도록 위치를 맞춰 주세요."
        SignInputPhase.Collecting -> "인식된 수어를 문장으로 묶고 있어요."
        SignInputPhase.Translating -> "수어 문장을 자연스러운 말로 바꾸는 중입니다."
        SignInputPhase.Fallback -> "서버 대신 기기 내 처리로 이어가고 있어요."
        SignInputPhase.Error -> "수어 인식을 일시적으로 처리하지 못했습니다."
    }

private fun SpeechInputPhase.title(): String =
    when (this) {
        SpeechInputPhase.Idle -> "대기 중"
        SpeechInputPhase.Listening -> "음성 듣는 중"
        SpeechInputPhase.Analyzing -> "음성 분석 중"
        SpeechInputPhase.Fallback -> "기기 인식 전환"
        SpeechInputPhase.Error -> "확인 필요"
    }

private fun SpeechInputPhase.description(): String =
    when (this) {
        SpeechInputPhase.Idle -> "대화를 시작하면 마이크 인식이 준비됩니다."
        SpeechInputPhase.Listening -> "상대방의 말을 듣고 자막으로 옮길 준비가 됐어요."
        SpeechInputPhase.Analyzing -> "방금 들은 음성을 분석하고 있어요."
        SpeechInputPhase.Fallback -> "서버 대신 기기 내 음성 인식으로 이어가고 있어요."
        SpeechInputPhase.Error -> "음성 인식을 일시적으로 처리하지 못했습니다."
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

private fun SignInputPhase.glossPlaceholder(): String =
    when (this) {
        SignInputPhase.Idle -> "세션 시작 전입니다."
        SignInputPhase.Preparing -> "수어 인식 준비 중..."
        SignInputPhase.NoHandsDetected -> "손을 화면 안에 보여 주세요."
        SignInputPhase.Translating -> "번역 중..."
        SignInputPhase.Fallback -> "기기 내 처리로 전환됨"
        SignInputPhase.Error -> "세션 상태를 확인해 주세요."
        else -> "수어 인식 대기 중..."
    }

private fun SignInputPhase.isWarning(): Boolean =
    this == SignInputPhase.NoHandsDetected ||
        this == SignInputPhase.Fallback ||
        this == SignInputPhase.Error

private fun SpeechInputPhase.isWarning(): Boolean =
    this == SpeechInputPhase.Fallback || this == SpeechInputPhase.Error

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
        currentContext = currentContext.baseContext
    }
    return null
}
