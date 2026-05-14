package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizResultAnswer
import java.util.Locale

@Composable
fun QuizResultRoute(
    onRestartQuiz: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    QuizResultScreen(
        uiState = uiState,
        onRestartQuiz = onRestartQuiz,
        onNavigateToHome = onNavigateToHome,
        onRetryLoad = viewModel::loadQuizResult,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizResultScreen(
    uiState: QuizResultUiState,
    onRestartQuiz: () -> Unit,
    onNavigateToHome: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "퀴즈 결과",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when (uiState) {
                is QuizResultUiState.Loading -> {
                    QuizMessageState(
                        title = "결과를 불러오는 중이에요",
                        description = "잠시만 기다려 주세요.",
                        visual = QuizMessageVisual.Loading,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is QuizResultUiState.Success -> {
                    QuizResultContent(
                        result = uiState.result,
                        onRestartQuiz = onRestartQuiz,
                        onNavigateToHome = onNavigateToHome,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is QuizResultUiState.Error -> {
                    QuizMessageState(
                        title = "결과를 불러오지 못했어요",
                        description = uiState.message,
                        actionText = "다시 시도",
                        onActionClick = onRetryLoad,
                        visual = QuizMessageVisual.Error,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizResultContent(
    result: LearningQuizResult,
    onRestartQuiz: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuizFeedbackEffects(
        eventKey = result.sessionId,
        cue = QuizFeedbackCue.Complete,
    )

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                QuizResultHeaderCard(
                    correctCount = result.correctCount,
                    totalCount = result.totalQuestionCount,
                    totalStar = result.totalStar,
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "문항별 결과",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    AppBadge(
                        text = "${result.correctCount} / ${result.totalQuestionCount}",
                        tone = AppBadgeTone.Primary,
                    )
                }
            }

            itemsIndexed(result.answers) { index, answer ->
                QuizAnswerResultItem(
                    answer = answer,
                    questionNumber = index + 1,
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppPrimaryButton(
                    text = "한 번 더 풀기",
                    onClick = onRestartQuiz,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppSecondaryButton(
                    text = "학습 홈으로",
                    onClick = onNavigateToHome,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuizResultHeaderCard(
    correctCount: Int,
    totalCount: Int,
    totalStar: Int,
) {
    val accuracyRate =
        if (totalCount == 0) {
            0.0
        } else {
            correctCount.toDouble() / totalCount.toDouble() * PERCENT_MAX
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 0.dp,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                    Color.Transparent,
                                ),
                        ),
                    ).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            QuizResultStarMedal(totalStar = totalStar)
            AppBadge(
                text = "퀴즈 완료",
                tone = AppBadgeTone.Success,
            )
            Text(
                text = "오늘도 잘했어요!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            QuizResultAccuracyBar(accuracyRate = accuracyRate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ResultStatItem(
                    label = "맞힌 문제",
                    value = "$correctCount / $totalCount",
                    badgeText = "정답",
                    tone = AppBadgeTone.Success,
                )
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            ),
                )
                ResultStatItem(
                    label = "모은 별",
                    value = "★ $totalStar",
                    badgeText = "별점",
                    tone = AppBadgeTone.Primary,
                )
            }
        }
    }
}

@Composable
private fun QuizResultStarMedal(totalStar: Int) {
    Surface(
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "★",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "$totalStar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun QuizResultAccuracyBar(accuracyRate: Double) {
    val animatedAccuracy by animateFloatAsState(
        targetValue = (accuracyRate / PERCENT_MAX).toFloat().coerceIn(0f, 1f),
        label = "quizResultAccuracy",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "정답률",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
            Text(
                text = String.format(Locale.KOREA, "%.0f%%", accuracyRate),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        LinearProgressIndicator(
            progress = { animatedAccuracy },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun ResultStatItem(
    label: String,
    value: String,
    badgeText: String,
    tone: AppBadgeTone,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppBadge(
            text = badgeText,
            tone = tone,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun QuizAnswerResultItem(
    answer: LearningQuizResultAnswer,
    questionNumber: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color =
            if (answer.isCorrect) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
            },
        contentColor = MaterialTheme.colorScheme.onSurface,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (answer.isCorrect) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBadge(
                    text = "${questionNumber}번",
                    tone = AppBadgeTone.Neutral,
                )

                Spacer(modifier = Modifier.width(8.dp))

                AppBadge(
                    text = if (answer.isCorrect) "잘했어요" else "아쉬워요",
                    tone = if (answer.isCorrect) AppBadgeTone.Success else AppBadgeTone.Error,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = answer.targetText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                QuizAnswerStarRating(star = answer.star)
            }

            QuizAnswerRecognizedText(recognizedText = answer.recognizedText)

            if (!answer.feedback.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = answer.feedback,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizAnswerStarRating(star: Int) {
    val normalizedStar = star.coerceIn(0, MAX_STAR_COUNT)
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(normalizedStar) {
            Text(
                text = "★",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        repeat(MAX_STAR_COUNT - normalizedStar) {
            Text(
                text = "☆",
                color = Color.Gray.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun QuizAnswerRecognizedText(recognizedText: String?) {
    val hasRecognizedText = !recognizedText.isNullOrBlank()
    val displayText = if (hasRecognizedText) recognizedText.orEmpty() else "음성 인식 결과가 없어요"

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "내가 말한 답",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight =
                if (hasRecognizedText) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
            color =
                if (hasRecognizedText) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_STAR_COUNT = 3
private const val PERCENT_MAX = 100.0
