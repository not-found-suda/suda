@file:Suppress("MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.home.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.R
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaMascotImage
import com.ssafy.mobile.core.ui.components.SudaStateView
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile
import com.ssafy.mobile.feature.childprofile.presentation.ChildProfileAvatars
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationAnalysisStatus
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationSessionSummary
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationSummary
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationWordCount
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun HomeRoute(
    onResumeQuiz: (ReportQuizSession) -> Unit,
    onViewAllResumeQuizzes: () -> Unit,
    onNavigateToChildSelect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면이 다시 보일 때마다 활성 아이 정보 갱신 (예: 전환 후 돌아왔을 때)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.loadActiveChildProfile(showLoading = false)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.activeChildState) {
        when (uiState.activeChildState) {
            ActiveChildProfileState.Missing,
            ActiveChildProfileState.NotFound,
            -> onNavigateToChildSelect()

            ActiveChildProfileState.Loading,
            is ActiveChildProfileState.Error,
            is ActiveChildProfileState.Selected,
            -> Unit
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeLogoHeader()

            val selectedProfile =
                (uiState.activeChildState as? ActiveChildProfileState.Selected)?.profile
            if (selectedProfile != null) {
                WeeklyLearningNoteCard(
                    profile = selectedProfile,
                    state = uiState.weeklyActivityState,
                    selectedDate = uiState.selectedCommunicationDate,
                    onDateSelected = viewModel::selectCommunicationDate,
                )

                CommunicationInsightSection(
                    state = uiState.communicationInsightState,
                    selectedDate = uiState.selectedCommunicationDate,
                    toggleResetKey = uiState.communicationInsightToggleResetKey,
                )

                ResumeQuizSection(
                    state = uiState.resumeQuizState,
                    onSessionClick = onResumeQuiz,
                    onViewAllClick = onViewAllResumeQuizzes,
                )
            }
        }
    }
}

@Composable
private fun HomeLogoHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.suda_wordmark),
            contentDescription = "SUDA",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        SudaMascotImage(
            mascot = SudaMascot.Default,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
    }
}

@Composable
private fun WeeklyLearningNoteCard(
    profile: ChildProfile,
    state: HomeWeeklyActivityState,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChildProfileAvatar(profile = profile)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "이번 주 학습 기록",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile.age?.let { "${profile.name} ${it}세" } ?: profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        HomeGuideBubble(
            mascot = SudaMascot.Icon,
            message = state.toWeeklyGuideMessage(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        HomeWeekDateStrip(
            state = state,
            selectedDate = selectedDate,
            onDateSelected = onDateSelected,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = state.toLearningNoteSentence(childName = profile.name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeGuideBubble(
    mascot: SudaMascot,
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SudaMascotImage(
                mascot = mascot,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChildProfileAvatar(
    profile: ChildProfile,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Image(
            painter = painterResource(id = ChildProfileAvatars.resourceId(profile.avatarKey)),
            contentDescription = "${profile.name} 프로필 이미지",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(7.dp),
        )
    }
}

@Composable
private fun HomeWeekDateStrip(
    state: HomeWeeklyActivityState,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val dates = currentWeekDates(anchorDate = selectedDate)
    val today =
        LocalDate
            .now()
            .takeUnless { it.isBefore(selectedDate) }
            ?: selectedDate

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dates.forEach { date ->
                HomeWeekDateCell(
                    date = date,
                    status = date.toHomeWeekDateStatus(today = today, state = state),
                    selected = date == selectedDate,
                    onClick = { onDateSelected(date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeWeekDateCell(
    date: LocalDate,
    status: HomeWeekDateStatus,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor =
        if (selected) {
            colors.secondaryContainer
        } else {
            when (status) {
                HomeWeekDateStatus.Completed -> colors.primary
                HomeWeekDateStatus.Missed -> colors.errorContainer
                HomeWeekDateStatus.Neutral -> colors.surface
            }
        }
    val contentColor =
        if (selected) {
            colors.onSecondaryContainer
        } else {
            when (status) {
                HomeWeekDateStatus.Completed -> colors.onPrimary
                HomeWeekDateStatus.Missed -> colors.onErrorContainer
                HomeWeekDateStatus.Neutral -> colors.onSurfaceVariant
            }
        }

    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(if (selected) 18.dp else 10.dp),
        color = containerColor,
        contentColor = contentColor,
        border =
            if (selected) {
                BorderStroke(2.dp, colors.secondary)
            } else {
                null
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = date.toWeekdayLabel(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CommunicationInsightSection(
    state: HomeCommunicationInsightState,
    selectedDate: LocalDate,
    toggleResetKey: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "소통 분석",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        when (state) {
            HomeCommunicationInsightState.Idle,
            HomeCommunicationInsightState.Loading,
            ->
                CommunicationInsightStatusCard(
                    mascot = SudaMascot.Loading,
                    title = "소통 분석을 확인하고 있어요",
                )

            HomeCommunicationInsightState.Empty ->
                CommunicationInsightStatusCard(
                    mascot = SudaMascot.Empty,
                    title = "${selectedDate.toCommunicationDateLabel()} 기록이 없어요",
                    description = "소통 화면에서 아이 음성을 기록하면 이곳에 보여드려요.",
                )

            HomeCommunicationInsightState.Processing ->
                CommunicationInsightStatusCard(
                    mascot = SudaMascot.Icon,
                    title = "최근 대화를 분석 중이에요",
                    description = "세션이 끝난 뒤 잠시 지나면 결과가 채워져요.",
                )

            is HomeCommunicationInsightState.Error ->
                CommunicationInsightStatusCard(
                    mascot = SudaMascot.ErrorNetwork,
                    title = "소통 분석을 불러오지 못했어요",
                    description = state.message,
                )

            is HomeCommunicationInsightState.Success ->
                CommunicationInsightSessionList(
                    summary = state.summary,
                    selectedDate = selectedDate,
                    toggleResetKey = toggleResetKey,
                )
        }
    }
}

@Composable
private fun CommunicationInsightSessionList(
    summary: ReportCommunicationSummary,
    selectedDate: LocalDate,
    toggleResetKey: Int,
) {
    var expandedSessionId by remember(toggleResetKey, selectedDate, summary.recentSessions) {
        mutableStateOf<Long?>(null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CommunicationInsightOverviewCard(
            summary = summary,
            selectedDate = selectedDate,
        )

        if (summary.recentSessions.isEmpty()) {
            CommunicationInsightStatusCard(
                mascot = SudaMascot.Empty,
                title = "이 날짜에는 세션 분석이 없어요",
                description = "다른 날짜를 눌러 저장된 대화를 확인해 보세요.",
            )
        } else {
            summary.recentSessions.forEachIndexed { index, session ->
                CommunicationSessionAnalysisCard(
                    session = session,
                    expanded = expandedSessionId == session.sessionId,
                    onToggle = {
                        expandedSessionId =
                            if (expandedSessionId == session.sessionId) {
                                null
                            } else {
                                session.sessionId
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun CommunicationInsightOverviewCard(
    summary: ReportCommunicationSummary,
    selectedDate: LocalDate,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SudaMascotImage(
                        mascot = SudaMascot.Microphone,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = selectedDate.toCommunicationDateLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppBadge(
                        text = "평균 ${summary.averageSentenceLength.toOneDecimal()}단어",
                        tone = AppBadgeTone.Tertiary,
                    )
                    AppBadge(
                        text = "세션 ${summary.totalSessionCount}개",
                        tone = AppBadgeTone.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunicationSessionAnalysisCard(
    session: ReportCommunicationSessionSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(18.dp),
                color = session.analysisStatus.toSoftContainerColor(),
                contentColor = session.analysisStatus.toSoftContentColor(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_report_boy),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(5.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.toTimeRangeLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        "아이 발화 ${session.utteranceCount}회 · " +
                            "평균 ${session.averageSentenceLength.toOneDecimal()}단어",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                AppBadge(
                    text = session.analysisStatus.toHomeStatusLabel(),
                    tone = session.analysisStatus.toHomeBadgeTone(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (expanded) "접기" else "보기",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (session.summary.isNotBlank() && !expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = session.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(14.dp))
            CommunicationSessionExpandedContent(session = session)
        }
    }
}

@Composable
private fun CommunicationSessionExpandedContent(session: ReportCommunicationSessionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (session.summary.isNotBlank()) {
            CommunicationDetailBubble(
                title = "요약",
                body = session.summary,
            )
        }

        CommunicationLevelDashboard(session = session)
        CommunicationExpressionChart(session = session)
        CommunicationWordChart(words = session.frequentWords)
        CommunicationTextList(title = "잘하고 있는 점", values = session.strengths)
        CommunicationTextList(title = "도와주면 좋은 점", values = session.improvementPoints)
        CommunicationTextList(title = "부모 대화 가이드", values = session.parentGuide)
        CommunicationTextList(title = "추천 놀이", values = session.recommendedActivities)

        if (session.developmentReference.isNotBlank()) {
            CommunicationDetailBubble(
                title = "발화 참고",
                body = session.developmentReference,
            )
        }

        if (session.consultationGuide.isNotBlank()) {
            CommunicationDetailBubble(
                title = "상담 참고",
                body = session.consultationGuide,
            )
        }
    }
}

@Composable
private fun CommunicationLevelDashboard(session: ReportCommunicationSessionSummary) {
    AnalysisPanel(title = "발화 성장 지표") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CommunicationMeterRow(
                title = "표현 흐름",
                label = session.communicationLevel.toHomeLevelLabel(),
                progress = session.communicationLevel.toLevelProgress(),
                color = MaterialTheme.colorScheme.primary,
            )
            CommunicationMeterRow(
                title = "단어 다양성",
                label = session.vocabularyDiversityLevel.toHomeLevelLabel(),
                progress = session.vocabularyDiversityLevel.toLevelProgress(),
                color = MaterialTheme.colorScheme.tertiary,
            )
            CommunicationMeterRow(
                title = "문장 확장",
                label = session.sentenceExpansionLevel.toHomeLevelLabel(),
                progress = session.sentenceExpansionLevel.toLevelProgress(),
                color = MaterialTheme.colorScheme.secondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CommunicationInsightMetric(
                    title = "아이 발화",
                    value = "${session.utteranceCount}회",
                    modifier = Modifier.weight(1f),
                )
                CommunicationInsightMetric(
                    title = "주의 신호",
                    value = session.cautionLevel.toCautionLabel(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CommunicationExpressionChart(session: ReportCommunicationSessionSummary) {
    val counts = session.expressionTypeCounts
    val entries =
        listOf(
            AnalysisChartEntry("요구", counts.request, MaterialTheme.colorScheme.primary),
            AnalysisChartEntry("감정", counts.emotion, MaterialTheme.colorScheme.secondary),
            AnalysisChartEntry("응답", counts.response, MaterialTheme.colorScheme.tertiary),
            AnalysisChartEntry("놀이", counts.play, MaterialTheme.colorScheme.primary),
            AnalysisChartEntry("질문", counts.question, MaterialTheme.colorScheme.secondary),
            AnalysisChartEntry("기타", counts.other, MaterialTheme.colorScheme.outline),
        )
    val total = counts.total

    AnalysisPanel(title = "표현 유형 그래프") {
        if (total <= 0) {
            EmptyAnalysisText(text = "분류된 표현 유형이 아직 없어요.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.forEach { entry ->
                    CommunicationBarRow(
                        title = entry.title,
                        valueText = "${entry.count}회",
                        progress = entry.count.toFloat() / total.toFloat(),
                        color = entry.color,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunicationWordChart(words: List<ReportCommunicationWordCount>) {
    val maxCount = words.maxOfOrNull { it.count } ?: 0

    AnalysisPanel(title = "자주 말한 단어") {
        if (words.isEmpty() || maxCount <= 0) {
            EmptyAnalysisText(text = "아직 자주 등장한 단어가 없어요.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                words.take(HOME_COMMUNICATION_WORD_BAR_COUNT).forEach { word ->
                    CommunicationBarRow(
                        title = word.word,
                        valueText = "${word.count}회",
                        progress = word.count.toFloat() / maxCount.toFloat(),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisPanel(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun CommunicationMeterRow(
    title: String,
    label: String,
    progress: Float,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
        CommunicationProgressBar(
            progress = progress,
            color = color,
        )
    }
}

@Composable
private fun CommunicationBarRow(
    title: String,
    valueText: String,
    progress: Float,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(58.dp),
        )
        CommunicationProgressBar(
            progress = progress,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CommunicationProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(10.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(10.dp),
                shape = RoundedCornerShape(999.dp),
                color = color,
                content = {},
            )
        }
    }
}

@Composable
private fun EmptyAnalysisText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class AnalysisChartEntry(
    val title: String,
    val count: Int,
    val color: Color,
)

@Composable
private fun CommunicationInsightMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CommunicationTextList(
    title: String,
    values: List<String>,
) {
    if (values.isEmpty()) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        values.take(HOME_COMMUNICATION_TEXT_COUNT).forEach { value ->
            Text(
                text = "• $value",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommunicationDetailBubble(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CommunicationInsightStatusCard(
    mascot: SudaMascot,
    title: String,
    description: String? = null,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SudaStateView(
            mascot = mascot,
            title = title,
            description = description,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(116.dp),
            compact = true,
        )
    }
}

@Composable
private fun ResumeQuizSection(
    state: HomeResumeQuizState,
    onSessionClick: (ReportQuizSession) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "이어하기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (state is HomeResumeQuizState.Success && state.hasHiddenSessions) {
                TextButton(onClick = onViewAllClick) {
                    Text(text = "전체 보기")
                }
            }
        }

        when (state) {
            HomeResumeQuizState.Idle,
            HomeResumeQuizState.Loading,
            -> ResumeQuizLoadingCard()

            HomeResumeQuizState.Empty -> ResumeQuizEmptyCard()

            is HomeResumeQuizState.Error ->
                ResumeQuizStatusCard(
                    mascot = SudaMascot.ErrorNetwork,
                    title = "이어하기를 불러오지 못했어요",
                    description = state.message,
                )

            is HomeResumeQuizState.Success ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sessions.forEach { session ->
                        ResumeQuizCard(
                            session = session,
                            onClick = { onSessionClick(session) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun ResumeQuizCard(
    session: ReportQuizSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SudaMascotImage(
                        mascot = SudaMascot.Microphone,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${session.categoryName} 퀴즈",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        "${session.difficulty.toHomeDifficultyLabel()} · " +
                            session.toStartedDateLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            AppBadge(
                text = "이어하기",
                tone = AppBadgeTone.Primary,
            )
        }
    }
}

@Composable
private fun ResumeQuizLoadingCard() {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SudaStateView(
            mascot = SudaMascot.Loading,
            title = "이어할 퀴즈를 확인하고 있어요",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(116.dp),
            compact = true,
        )
    }
}

@Composable
private fun ResumeQuizEmptyCard() {
    ResumeQuizStatusCard(
        mascot = SudaMascot.Empty,
        title = "지금 이어서 풀 퀴즈가 없어요",
        description = "새 퀴즈를 시작하면 여기에서 이어갈 수 있어요.",
    )
}

@Composable
private fun ResumeQuizStatusCard(
    mascot: SudaMascot,
    title: String,
    description: String? = null,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SudaStateView(
            mascot = mascot,
            title = title,
            description = description,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(124.dp),
            compact = true,
        )
    }
}

private fun HomeWeeklyActivityState.toWeeklyGuideMessage(): String =
    when (this) {
        HomeWeeklyActivityState.Idle,
        HomeWeeklyActivityState.Loading,
        -> "이번 주 기록을 정리하고 있어요."
        HomeWeeklyActivityState.Error -> "잠시 후 다시 확인해볼게요."
        is HomeWeeklyActivityState.Success ->
            if (activityDates.isEmpty()) {
                "오늘부터 한 장씩 시작해볼까요?"
            } else {
                "좋아요, 이번 주도 차근차근 쌓고 있어요."
            }
    }

private fun currentWeekDates(anchorDate: LocalDate): List<LocalDate> {
    val weekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0 until DAYS_IN_WEEK).map { offset ->
        weekStart.plusDays(offset.toLong())
    }
}

private enum class HomeWeekDateStatus {
    Completed,
    Missed,
    Neutral,
}

private fun LocalDate.toHomeWeekDateStatus(
    today: LocalDate,
    state: HomeWeeklyActivityState,
): HomeWeekDateStatus {
    if (isAfter(today)) {
        return HomeWeekDateStatus.Neutral
    }

    return when (state) {
        is HomeWeeklyActivityState.Success ->
            if (this in state.activityDates) {
                HomeWeekDateStatus.Completed
            } else {
                HomeWeekDateStatus.Missed
            }
        else -> HomeWeekDateStatus.Neutral
    }
}

private fun LocalDate.toWeekdayLabel(): String =
    when (dayOfWeek.value) {
        1 -> "월"
        2 -> "화"
        3 -> "수"
        4 -> "목"
        5 -> "금"
        6 -> "토"
        else -> "일"
    }

private fun HomeWeeklyActivityState.toLearningNoteSentence(childName: String): String =
    when (this) {
        HomeWeeklyActivityState.Idle,
        HomeWeeklyActivityState.Loading,
        -> "${childName}의 이번 주 학습 기록을 정리하고 있어요."
        HomeWeeklyActivityState.Error ->
            "이번 주 학습 기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
        is HomeWeeklyActivityState.Success -> {
            val activeDayCount = activityDates.size
            if (activeDayCount == 0) {
                "${childName}의 이번 주 학습 기록이 아직 없어요."
            } else {
                "${childName}는 이번 주 ${activeDayCount}일 동안 학습했어요."
            }
        }
    }

private fun ReportCommunicationAnalysisStatus.toHomeStatusLabel(): String =
    when (this) {
        ReportCommunicationAnalysisStatus.Pending -> "대기"
        ReportCommunicationAnalysisStatus.Processing -> "분석 중"
        ReportCommunicationAnalysisStatus.Completed -> "분석 완료"
        ReportCommunicationAnalysisStatus.Failed -> "실패"
        ReportCommunicationAnalysisStatus.Empty -> "기록 없음"
        ReportCommunicationAnalysisStatus.Unknown -> "확인"
    }

private fun ReportCommunicationAnalysisStatus.toHomeBadgeTone(): AppBadgeTone =
    when (this) {
        ReportCommunicationAnalysisStatus.Pending -> AppBadgeTone.Warning
        ReportCommunicationAnalysisStatus.Processing -> AppBadgeTone.Primary
        ReportCommunicationAnalysisStatus.Completed -> AppBadgeTone.Success
        ReportCommunicationAnalysisStatus.Failed -> AppBadgeTone.Error
        ReportCommunicationAnalysisStatus.Empty,
        ReportCommunicationAnalysisStatus.Unknown,
        -> AppBadgeTone.Neutral
    }

private fun String.toHomeLevelLabel(): String =
    when (uppercase(Locale.ROOT)) {
        "LOW" -> "차근차근"
        "NORMAL" -> "안정적"
        "HIGH" -> "활발함"
        else -> "확인 중"
    }

private fun String.toLevelProgress(): Float =
    when (uppercase(Locale.ROOT)) {
        "LOW" -> LOW_LEVEL_PROGRESS
        "NORMAL" -> NORMAL_LEVEL_PROGRESS
        "HIGH" -> HIGH_LEVEL_PROGRESS
        else -> UNKNOWN_LEVEL_PROGRESS
    }

private fun String.toCautionLabel(): String =
    when (uppercase(Locale.ROOT)) {
        "WATCH" -> "관찰"
        "CONSULT" -> "상담 참고"
        else -> "없음"
    }

private fun LocalDate.toCommunicationDateLabel(): String =
    "${monthValue}월 ${dayOfMonth}일 ${toWeekdayLabel()}요일 소통"

private fun Double.toOneDecimal(): String = String.format(Locale.KOREA, "%.1f", this)

private fun ReportCommunicationSessionSummary.toTimeRangeLabel(): String {
    val start = startedAt.toTimeLabel() ?: "시간 정보 없음"
    val end = endedAt.toTimeLabel()
    return if (end == null) {
        start
    } else {
        "$start ~ $end"
    }
}

private fun String?.toTimeLabel(): String? =
    takeUnless { it.isNullOrBlank() }
        ?.drop(ISO_DATE_TIME_SEPARATOR_INDEX)
        ?.take(ISO_TIME_LENGTH)
        ?.takeIf { it.length == ISO_TIME_LENGTH }

@Composable
private fun ReportCommunicationAnalysisStatus.toSoftContainerColor(): Color =
    when (this) {
        ReportCommunicationAnalysisStatus.Completed ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
        ReportCommunicationAnalysisStatus.Processing,
        ReportCommunicationAnalysisStatus.Pending,
        -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
        ReportCommunicationAnalysisStatus.Failed ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
        ReportCommunicationAnalysisStatus.Empty,
        ReportCommunicationAnalysisStatus.Unknown,
        -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    }

@Composable
private fun ReportCommunicationAnalysisStatus.toSoftContentColor(): Color =
    when (this) {
        ReportCommunicationAnalysisStatus.Completed ->
            MaterialTheme.colorScheme.onPrimaryContainer
        ReportCommunicationAnalysisStatus.Processing,
        ReportCommunicationAnalysisStatus.Pending,
        -> MaterialTheme.colorScheme.onSecondaryContainer
        ReportCommunicationAnalysisStatus.Failed ->
            MaterialTheme.colorScheme.onErrorContainer
        ReportCommunicationAnalysisStatus.Empty,
        ReportCommunicationAnalysisStatus.Unknown,
        -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun String.toHomeDifficultyLabel(): String =
    when (this) {
        "EASY" -> "쉬움"
        "NORMAL" -> "보통"
        "HARD" -> "어려움"
        else -> this
    }

private fun ReportQuizSession.toStartedDateLabel(): String =
    startedAt
        ?.take(ISO_DATE_LENGTH)
        ?.replace("-", ".")
        ?: "시작일 정보 없음"

private val HomeResumeQuizState.Success.hasHiddenSessions: Boolean
    get() = totalElements > sessions.size

private const val DAYS_IN_WEEK = 7
private const val ISO_DATE_LENGTH = 10
private const val ISO_DATE_TIME_SEPARATOR_INDEX = 11
private const val ISO_TIME_LENGTH = 5
private const val HOME_COMMUNICATION_TEXT_COUNT = 5
private const val HOME_COMMUNICATION_WORD_BAR_COUNT = 5
private const val LOW_LEVEL_PROGRESS = 0.34f
private const val NORMAL_LEVEL_PROGRESS = 0.67f
private const val HIGH_LEVEL_PROGRESS = 1f
private const val UNKNOWN_LEVEL_PROGRESS = 0f
