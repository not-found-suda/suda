@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "UnusedPrivateMember",
)

package com.ssafy.mobile.feature.sign.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignDebugRoute(
    modifier: Modifier = Modifier,
    onBackToMain: () -> Unit = {},
    viewModel: SignDebugViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLlmDebugDialog by rememberSaveable { mutableStateOf(false) }
    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(viewModel::startVideoReplay)
        }
    val stopAndBack = {
        viewModel.stop()
        onBackToMain()
    }

    BackHandler(onBack = stopAndBack)

    if (showLlmDebugDialog) {
        LlmDebugDialog(
            uiState = uiState,
            onDismiss = { showLlmDebugDialog = false },
            onInputChanged = viewModel::updateLlmInput,
            onRunTest = viewModel::runLlmTest,
        )
    }

    SignDebugScreen(
        uiState = uiState,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        onBackToMain = stopAndBack,
        onAnalysisFrame = viewModel::onAnalysisFrame,
        onLandmarkFrame = viewModel::onLandmarkFrame,
        onCameraMetrics = viewModel::onCameraMetrics,
        onSaveAnalysisFrame = viewModel::saveCurrentAnalysisFrame,
        onStartAnalysisRecording = viewModel::startAnalysisRecording,
        onStopAnalysisRecording = viewModel::stopAnalysisRecording,
        onCancelAnalysisRecording = viewModel::cancelAnalysisRecording,
        onCycleResolution = viewModel::cycleResolution,
        onCycleTargetFps = viewModel::cycleTargetFps,
        onCycleAnalysisFrameInterval = viewModel::cycleAnalysisFrameInterval,
        onToggleMirrorAnalysisInput = viewModel::toggleMirrorAnalysisInput,
        onCycleThreshold = viewModel::cycleThreshold,
        onCycleSmoothing = viewModel::cycleSmoothing,
        onPickReplayVideo = { videoPickerLauncher.launch("video/*") },
        onStopVideoReplay = viewModel::stopVideoReplay,
        onOpenLlmDebug = { showLlmDebugDialog = true },
        modifier = modifier,
    )
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun SignDebugScreen(
    uiState: SignDebugUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBackToMain: () -> Unit,
    onAnalysisFrame: (YuvAnalysisFrame) -> Unit,
    onLandmarkFrame: (LandmarkFrameResult) -> Unit,
    onCameraMetrics: (CameraPerformanceMetrics) -> Unit,
    onSaveAnalysisFrame: () -> Unit,
    onStartAnalysisRecording: () -> Unit,
    onStopAnalysisRecording: () -> Unit,
    onCancelAnalysisRecording: () -> Unit,
    onCycleResolution: () -> Unit,
    onCycleTargetFps: () -> Unit,
    onCycleAnalysisFrameInterval: () -> Unit,
    onToggleMirrorAnalysisInput: () -> Unit,
    onCycleThreshold: () -> Unit,
    onCycleSmoothing: () -> Unit,
    onPickReplayVideo: () -> Unit,
    onStopVideoReplay: () -> Unit,
    onOpenLlmDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 세로 모드(Portrait)를 유지하여 카메라 센서가 1080x1920 원본 비율을 유지하도록 합니다.

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF111827), Color(0xFF020617)),
                        ),
                    ).padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val cameraModifier = Modifier.fillMaxSize()

                SignRecognitionScreen(
                    isSessionActive = uiState.isRunning,
                    cameraAnalysisSettings = uiState.cameraSettings,
                    showDebugOverlay = true,
                    onFrameAvailable = onAnalysisFrame,
                    onLandmarkFrameAvailable = onLandmarkFrame,
                    onCameraMetricsChanged = onCameraMetrics,
                    modifier = cameraModifier,
                )

                var displayWord by remember { mutableStateOf<String?>(null) }
                var showStartPrompt by remember { mutableStateOf(false) }

                LaunchedEffect(uiState.currentGloss, uiState.confidence) {
                    if (uiState.currentGloss != "-" && uiState.currentGloss != "none") {
                        val confidenceText = formatPercent(uiState.confidence)
                        displayWord = "인식됨: ${uiState.currentGloss} ($confidenceText)"
                        delay(2000L) // 2초 유지
                        displayWord = null
                    }
                }

                LaunchedEffect(uiState.hasHands) {
                    if (uiState.hasHands) {
                        showStartPrompt = true
                        delay(1000L) // 1초 유지
                        showStartPrompt = false
                    } else {
                        showStartPrompt = false
                    }
                }

                if (uiState.isRunning) {
                    val textToShow =
                        when {
                            displayWord != null -> displayWord
                            !uiState.hasHands -> "카메라에 손을 보여주세요"
                            showStartPrompt -> "지금부터 수어를 시작하세요! \uD83E\uDD1F"
                            else -> null
                        }

                    if (textToShow != null) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                                    .background(
                                        color = Color(0xCC000000),
                                        shape = RoundedCornerShape(12.dp),
                                    ).padding(
                                        horizontal = 20.dp,
                                        vertical = 10.dp,
                                    ),
                        ) {
                            val textColor =
                                when {
                                    displayWord != null -> Color(0xFF38BDF8) // 하늘색 (인식됨)
                                    !uiState.hasHands -> Color(0xFF94A3B8) // 회색 (대기중)
                                    else -> Color(0xFF4ADE80) // 연두색 (시작 안내)
                                }

                            Text(
                                text = textToShow,
                                color = textColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (uiState.isRunning) {
                    val leftHandStatus = if (uiState.leftHandLandmarkCount > 0) "✅" else "❌"
                    val rightHandStatus = if (uiState.rightHandLandmarkCount > 0) "✅" else "❌"
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 16.dp, start = 16.dp)
                                .background(
                                    color = Color(0x99000000),
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(12.dp),
                    ) {
                        Column {
                            Text(
                                text = "왼손 인식: $leftHandStatus",
                                color =
                                    if (uiState.leftHandLandmarkCount > 0) {
                                        Color(0xFF4ADE80)
                                    } else {
                                        Color(0xFFF87171)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "오른손 인식: $rightHandStatus",
                                color =
                                    if (uiState.rightHandLandmarkCount > 0) {
                                        Color(0xFF4ADE80)
                                    } else {
                                        Color(0xFFF87171)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 1.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactDebugHeader(
                    isRunning = uiState.isRunning,
                    isModelReady = uiState.isModelReady,
                    onBackToMain = onBackToMain,
                )
                RunControls(
                    isRunning = uiState.isRunning,
                    onStart = onStart,
                    onStop = onStop,
                )
                AnalysisRecordingFrameCard(
                    uiState = uiState,
                    onSaveAnalysisFrame = onSaveAnalysisFrame,
                    onStartAnalysisRecording = onStartAnalysisRecording,
                    onStopAnalysisRecording = onStopAnalysisRecording,
                    onCancelAnalysisRecording = onCancelAnalysisRecording,
                )
                VideoReplayCard(
                    uiState = uiState,
                    onPickReplayVideo = onPickReplayVideo,
                    onStopVideoReplay = onStopVideoReplay,
                )
                RecognitionStatusCard(uiState)
                PerformanceCard(uiState)
                LlmDebugLauncherCard(
                    uiState = uiState,
                    onOpenLlmDebug = onOpenLlmDebug,
                )
                TuningCard(
                    uiState = uiState,
                    onCycleResolution = onCycleResolution,
                    onCycleTargetFps = onCycleTargetFps,
                    onCycleAnalysisFrameInterval = onCycleAnalysisFrameInterval,
                    onToggleMirrorAnalysisInput = onToggleMirrorAnalysisInput,
                    onCycleThreshold = onCycleThreshold,
                    onCycleSmoothing = onCycleSmoothing,
                )
                uiState.errorMessage?.let { errorMessage ->
                    ErrorCard(message = errorMessage)
                }
            }
        }
    }
}

// ForceLandscapeOrientationEffect removed

@Composable
private fun CompactDebugHeader(
    isRunning: Boolean,
    isModelReady: Boolean,
    onBackToMain: () -> Unit,
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "수어 디버그",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBackToMain) {
                    Text("뒤로", style = MaterialTheme.typography.labelSmall)
                }
                StatusPill(
                    label =
                        when {
                            !isModelReady -> "로딩"
                            isRunning -> "실행"
                            else -> "대기"
                        },
                    color =
                        when {
                            !isModelReady -> Color(0xFFF59E0B)
                            isRunning -> Color(0xFF22C55E)
                            else -> Color(0xFF64748B)
                        },
                )
            }
        }
    }
}

@Composable
private fun DebugHeader(
    isRunning: Boolean,
    isModelReady: Boolean,
    onBackToMain: () -> Unit,
) {
    Surface(color = Color(0xFF0F172A)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "수어 디버그",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "실기기 성능 측정",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBackToMain) {
                    Text("메인으로")
                }
                StatusPill(
                    label =
                        when {
                            !isModelReady -> "모델 로딩 중"
                            isRunning -> "측정 중"
                            else -> "정지"
                        },
                    color =
                        when {
                            !isModelReady -> Color(0xFFF59E0B)
                            isRunning -> Color(0xFF22C55E)
                            else -> Color(0xFF64748B)
                        },
                )
            }
        }
    }
}

@Composable
private fun RunControls(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactDebugButton(
            text = "시작",
            onClick = onStart,
            enabled = !isRunning,
            modifier = Modifier.weight(1f),
        )
        CompactDebugButton(
            text = "정지",
            onClick = onStop,
            enabled = isRunning,
            modifier = Modifier.weight(1f),
            outlined = true,
        )
    }
}

@Composable
private fun AnalysisFrameCard(
    uiState: SignDebugUiState,
    onSaveAnalysisFrame: () -> Unit,
) {
    DebugCard(title = "분석 화면") {
        Button(
            onClick = onSaveAnalysisFrame,
            enabled = uiState.isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("PNG 저장", style = MaterialTheme.typography.labelSmall)
        }
        MetricRow(
            "크기",
            if (uiState.analysisImageSize.width == 0) {
                "-"
            } else {
                "${uiState.analysisImageSize.width}x${uiState.analysisImageSize.height}"
            },
        )
        uiState.debugFrameSaveMessage?.let { message ->
            Text(
                text = message,
                color = Color(0xFFBAE6FD),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CompactDebugButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    val buttonModifier = modifier.height(30.dp)
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            contentPadding = contentPadding,
        ) {
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            contentPadding = contentPadding,
        ) {
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AnalysisRecordingFrameCard(
    uiState: SignDebugUiState,
    onSaveAnalysisFrame: () -> Unit,
    onStartAnalysisRecording: () -> Unit,
    onStopAnalysisRecording: () -> Unit,
    onCancelAnalysisRecording: () -> Unit,
) {
    DebugCard(title = "분석 화면") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactDebugButton(
                text = "PNG",
                onClick = onSaveAnalysisFrame,
                enabled = uiState.isRunning,
                modifier = Modifier.weight(1f),
            )
            CompactDebugButton(
                text = if (uiState.isAnalysisRecording) "저장" else "녹화",
                onClick =
                    if (uiState.isAnalysisRecording) {
                        onStopAnalysisRecording
                    } else {
                        onStartAnalysisRecording
                    },
                enabled = uiState.isRunning || uiState.isAnalysisRecording,
                modifier = Modifier.weight(1f),
                outlined = uiState.isAnalysisRecording,
            )
            if (uiState.isAnalysisRecording) {
                CompactDebugButton(
                    text = "취소",
                    onClick = onCancelAnalysisRecording,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    outlined = true,
                )
            }
        }
        MetricRow(
            "크기",
            if (uiState.analysisImageSize.width == 0) {
                "-"
            } else {
                "${uiState.analysisImageSize.width}x${uiState.analysisImageSize.height}"
            },
        )
        uiState.debugFrameSaveMessage?.let { message ->
            Text(
                text = message,
                color = Color(0xFFBAE6FD),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        uiState.analysisRecordingMessage?.let { message ->
            Text(
                text = message,
                color = Color(0xFFBAE6FD),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun VideoReplayCard(
    uiState: SignDebugUiState,
    onPickReplayVideo: () -> Unit,
    onStopVideoReplay: () -> Unit,
) {
    DebugCard(title = "영상 리플레이") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactDebugButton(
                text = "영상 선택",
                onClick = onPickReplayVideo,
                enabled = !uiState.isRunning && !uiState.isReplayRunning,
                modifier = Modifier.weight(1f),
            )
            CompactDebugButton(
                text = "중지",
                onClick = onStopVideoReplay,
                enabled = uiState.isReplayRunning,
                modifier = Modifier.weight(1f),
                outlined = true,
            )
        }
        MetricRow("파일", uiState.replayVideoName)
        MetricRow(
            "시도",
            "${uiState.replayAttemptedFrameCount}/${uiState.replayTotalFrameCount} 프레임",
        )
        MetricRow(
            "디코딩",
            "${uiState.replayProcessedFrameCount} 프레임",
        )
        MetricRow("길이", "${uiState.replayDurationMs / MILLIS_PER_SECOND}초")
        MetricRow("샘플링", "33ms 간격")
        uiState.replayStatusMessage?.let { message ->
            Text(
                text = message,
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        uiState.replayWholeVideoPrediction?.let { prediction ->
            Text(
                text = "전체 영상 Word Spotting",
                modifier = Modifier.padding(top = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = prediction,
                color = Color(0xFFBAE6FD),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (uiState.replayRawPredictions.isNotEmpty()) {
            MetricRow("raw 추론 수", "${uiState.replayRawPredictions.size}개")
            Text(
                text = "전체 raw 추론",
                modifier = Modifier.padding(top = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = uiState.replayRawPredictions.joinToString(separator = "\n"),
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (uiState.replayPredictions.isNotEmpty()) {
            Text(
                text = "최근 확정 인식",
                modifier = Modifier.padding(top = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = uiState.replayPredictions.joinToString(separator = "\n"),
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RecognitionStatusCard(uiState: SignDebugUiState) {
    DebugCard(title = "인식 상태") {
        MetricRow("현재 단어", uiState.currentGloss)
        MetricRow("신뢰도", formatPercent(uiState.confidence))
        MetricRow("손 감지", if (uiState.hasHands) "감지됨" else "미감지")
        MetricRow(
            "포즈 / 왼손 / 오른손 / 입",
            "${uiState.poseLandmarkCount} / " +
                "${uiState.leftHandLandmarkCount} / " +
                "${uiState.rightHandLandmarkCount} / " +
                "${uiState.lipLandmarkCount}",
        )
        MetricRow(
            "시퀀스",
            "${uiState.sequenceFrameCount}/30 프레임, " +
                "손 프레임 ${uiState.sequenceHandFrameCount}",
        )
    }
}

@Composable
private fun PerformanceCard(uiState: SignDebugUiState) {
    DebugCard(title = "성능 지표") {
        MetricRow("카메라 FPS", formatDecimal(uiState.cameraFps))
        MetricRow("카메라 프레임", uiState.cameraFrameCount.toString())
        MetricRow("MediaPipe 처리", "${formatDecimal(uiState.mediaPipeMs)} ms")
        MetricRow("TFLite 추론", "${formatDecimal(uiState.tfliteInferenceMs)} ms")
        MetricRow("전체 지연", "${formatDecimal(uiState.pipelineLatencyMs)} ms")
        MetricRow(
            "분석 해상도",
            if (uiState.analysisImageSize.width == 0) {
                "-"
            } else {
                "${uiState.analysisImageSize.width}x${uiState.analysisImageSize.height}"
            },
        )
    }
}

@Composable
private fun LlmDebugLauncherCard(
    uiState: SignDebugUiState,
    onOpenLlmDebug: () -> Unit,
) {
    DebugCard(title = "LLM 테스트") {
        CompactDebugButton(
            text = "열기",
            onClick = onOpenLlmDebug,
            modifier = Modifier.fillMaxWidth(),
        )
        MetricRow("상태", uiState.llmStage)
        uiState.llmOutput?.let { output ->
            Text(
                text = output,
                color = Color(0xFFBAE6FD),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LlmDebugDialog(
    uiState: SignDebugUiState,
    onDismiss: () -> Unit,
    onInputChanged: (String) -> Unit,
    onRunTest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
            color = llmPanelColor(uiState.llmStatus),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "LLM 테스트",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    CompactDebugButton(
                        text = "닫기",
                        onClick = onDismiss,
                        outlined = true,
                    )
                }
                OutlinedTextField(
                    value = uiState.llmInput,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gloss 입력") },
                    placeholder = { Text("예: 나 내일 학교 가다") },
                    minLines = 2,
                    maxLines = 4,
                )
                CompactDebugButton(
                    text = if (uiState.llmIsRunning) "실행 중" else "LLM 테스트 실행",
                    onClick = onRunTest,
                    enabled = !uiState.llmIsRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = uiState.llmStage,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = uiState.llmSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E8F0),
                )
                LlmResultPanel(uiState = uiState)
                if (uiState.llmDetail.isNotBlank()) {
                    Text(
                        text = uiState.llmDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                }
            }
        }
    }
}

@Composable
private fun LlmResultPanel(uiState: SignDebugUiState) {
    val resultText =
        when {
            uiState.llmOutput != null -> uiState.llmOutput
            uiState.llmIsRunning -> "생성 중입니다. 완료되면 이 영역에 바로 표시됩니다."
            else -> "아직 생성된 결과가 없습니다."
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "생성 결과",
                color = Color(0xFF93C5FD),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = resultText,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuningCard(
    uiState: SignDebugUiState,
    onCycleResolution: () -> Unit,
    onCycleTargetFps: () -> Unit,
    onCycleAnalysisFrameInterval: () -> Unit,
    onToggleMirrorAnalysisInput: () -> Unit,
    onCycleThreshold: () -> Unit,
    onCycleSmoothing: () -> Unit,
) {
    DebugCard(title = "튜닝 설정") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TuningButton(
                label =
                    "해상도 ${uiState.cameraSettings.targetResolution.width}x" +
                        uiState.cameraSettings.targetResolution.height,
                onClick = onCycleResolution,
            )
            TuningButton(
                label = "목표 FPS ${uiState.cameraSettings.targetFps}",
                onClick = onCycleTargetFps,
            )
            TuningButton(
                label = "분석 간격 ${uiState.cameraSettings.analysisFrameInterval}",
                onClick = onCycleAnalysisFrameInterval,
            )
            TuningButton(
                label =
                    if (uiState.cameraSettings.mirrorAnalysisInput) {
                        "분석 좌우반전 켬"
                    } else {
                        "분석 좌우반전 끔"
                    },
                onClick = onToggleMirrorAnalysisInput,
            )
            TuningButton(
                label = "임계값 ${formatPercent(uiState.recognitionConfig.confidenceThreshold)}",
                onClick = onCycleThreshold,
            )
            TuningButton(
                label =
                    "스무딩 ${uiState.recognitionConfig.smoothingRequiredVotes}/" +
                        uiState.recognitionConfig.smoothingWindowSize,
                onClick = onCycleSmoothing,
            )
            TuningButton(
                label = "시퀀스 고정 ${uiState.recognitionConfig.sequenceLength}",
                onClick = {},
                enabled = false,
            )
        }
        Text(
            text =
                "기본값: 1920x1080, 30 FPS, " +
                    "분석 간격 1, 시퀀스 30, 임계값 0.75",
            modifier = Modifier.padding(top = 6.dp),
            color = Color(0xFFCBD5E1),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TuningButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(30.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ErrorCard(message: String) {
    DebugCard(title = "오류", containerColor = Color(0xFF7F1D1D)) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DebugCard(
    title: String,
    containerColor: Color = Color(0xFF1E293B),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .background(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(100.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun llmPanelColor(status: SignDebugLlmStatus): Color =
    when (status) {
        SignDebugLlmStatus.Idle -> Color(0xFF1E293B)
        SignDebugLlmStatus.Running -> Color(0xFF5B3D00)
        SignDebugLlmStatus.Success -> Color(0xFF14532D)
        SignDebugLlmStatus.Failure -> Color(0xFF7F1D1D)
    }

private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun formatPercent(value: Float): String = String.format(Locale.US, "%.1f%%", value * 100f)

private const val MILLIS_PER_SECOND = 1_000L
private const val DEBUG_CAMERA_ASPECT_RATIO = 9f / 16f
