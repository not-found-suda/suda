@file:Suppress("MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.home.presentation

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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

        HomeWeekDateStrip(state = state)

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
private fun HomeWeekDateStrip(state: HomeWeeklyActivityState) {
    val dates = currentWeekDates()
    val today = LocalDate.now()

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
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor =
        when (status) {
            HomeWeekDateStatus.Completed -> colors.primary
            HomeWeekDateStatus.Missed -> colors.errorContainer
            HomeWeekDateStatus.Neutral -> colors.surface
        }
    val contentColor =
        when (status) {
            HomeWeekDateStatus.Completed -> colors.onPrimary
            HomeWeekDateStatus.Missed -> colors.onErrorContainer
            HomeWeekDateStatus.Neutral -> colors.onSurfaceVariant
        }

    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor,
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

private fun currentWeekDates(): List<LocalDate> {
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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
