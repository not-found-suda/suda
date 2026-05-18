@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.ChunkyButton
import com.ssafy.mobile.core.ui.components.ChunkyButtonTone
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaMascotImage
import com.ssafy.mobile.core.ui.theme.SudaWarning
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizResult
import kotlinx.coroutines.delay

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
                        description = "오늘 모은 별을 정리하고 있어요.",
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

    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color(0xFFD7FFF1),
                            Color(0xFFE8FFF8),
                            Color(0xFFFDFDF9),
                        ),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 34.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    QuizResultHeaderCard(result = result)
                }
            }

            QuizResultBottomActions(
                onRestartQuiz = onRestartQuiz,
                onNavigateToHome = onNavigateToHome,
            )
        }
    }
}

@Composable
private fun QuizResultHeaderCard(result: LearningQuizResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = Color.White.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SudaMascotImage(
                mascot = SudaMascot.Success3Star,
                contentDescription = null,
                modifier = Modifier.size(150.dp),
            )
            Text(
                text = "말놀이 완료!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            AnimatedMedalStars(starCount = COMPLETION_DECORATION_STAR_COUNT)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ResultStatItem(
                    label = "배운 단어",
                    value = "${result.correctCount} / ${result.totalQuestionCount}",
                )
                VerticalDivider()
                ResultStatItem(
                    label = "말한 단어",
                    value = "${result.answers.size}",
                )
            }
        }
    }
}

@Composable
private fun AnimatedMedalStars(starCount: Int) {
    var visibleStar by remember(starCount) { mutableIntStateOf(0) }
    val infiniteTransition = rememberInfiniteTransition(label = "resultStarPulse")
    val pulse by
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.06f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 760),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "resultStarPulseScale",
        )

    LaunchedEffect(starCount) {
        visibleStar = 0
        repeat(starCount.coerceIn(0, COMPLETION_DECORATION_STAR_COUNT)) { index ->
            delay(STAR_REVEAL_DELAY_MILLIS)
            visibleStar = index + 1
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(COMPLETION_DECORATION_STAR_COUNT) { index ->
            val isVisible = index < visibleStar
            AnimatedVisibility(visible = isVisible) {
                Surface(
                    modifier = Modifier.size(42.dp).scale(pulse),
                    shape = CircleShape,
                    color = SudaWarning.copy(alpha = if (isVisible) 1f else 0.25f),
                    contentColor = Color.White,
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "★",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultStatItem(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(48.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun QuizResultBottomActions(
    onRestartQuiz: () -> Unit,
    onNavigateToHome: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChunkyButton(
                text = "다른 주제 하기",
                onClick = onNavigateToHome,
                tone = ChunkyButtonTone.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                text = "다시 하기",
                onClick = onRestartQuiz,
                tone = ChunkyButtonTone.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val COMPLETION_DECORATION_STAR_COUNT = 5
private const val STAR_REVEAL_DELAY_MILLIS = 180L
