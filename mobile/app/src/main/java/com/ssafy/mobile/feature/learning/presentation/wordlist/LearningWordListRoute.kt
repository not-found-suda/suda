package com.ssafy.mobile.feature.learning.presentation.wordlist

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppInlineErrorText
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppNetworkImage
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.feedback.AppEmptyState
import com.ssafy.mobile.feature.learning.domain.model.LearningWord

@Composable
fun LearningWordListRoute(
    onNavigateBack: () -> Unit,
    onStartQuiz: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LearningWordListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            // 화면 이탈 시 오디오 정지
            viewModel.stopAudio()
        }
    }

    LearningWordListScreen(
        categoryName = viewModel.categoryName,
        uiState = uiState,
        onBackClick = onNavigateBack,
        actions =
            WordLearningActions(
                onPlayAudio = viewModel::playCurrentWordAudio,
                onStopAudio = viewModel::stopAudio,
                onNextClick = viewModel::nextWord,
                onPreviousClick = viewModel::previousWord,
                onStartQuiz = { onStartQuiz(viewModel.categoryId, viewModel.difficulty) },
                onRetryClick = viewModel::loadWords,
            ),
        modifier = modifier,
    )
}

internal data class WordLearningActions(
    val onPlayAudio: () -> Unit,
    val onStopAudio: () -> Unit,
    val onNextClick: () -> Unit,
    val onPreviousClick: () -> Unit,
    val onStartQuiz: () -> Unit,
    val onRetryClick: () -> Unit,
)

@Composable
internal fun LearningWordListScreen(
    categoryName: String?,
    uiState: LearningWordListUiState,
    onBackClick: () -> Unit,
    actions: WordLearningActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WordListHeader(
                categoryName = categoryName ?: "단어 학습",
                onBackClick = onBackClick,
            )

            Box(modifier = Modifier.weight(1f)) {
                when (uiState) {
                    is LearningWordListUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppLoadingIndicator(message = "단어 카드를 준비하고 있어요.")
                        }
                    }

                    is LearningWordListUiState.Success -> {
                        WordLearningCard(
                            state = uiState,
                            actions = actions,
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                        )
                    }

                    is LearningWordListUiState.Empty -> {
                        AppEmptyState(message = "준비된 단어가 없습니다.")
                    }

                    is LearningWordListUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AppInlineErrorText(text = uiState.message)
                            Spacer(modifier = Modifier.height(16.dp))
                            AppPrimaryButton(
                                text = "다시 시도",
                                onClick = actions.onRetryClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordListHeader(
    categoryName: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBackClick) {
                Text(
                    text = "← 뒤로",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            AppBadge(
                text = "단어장",
                tone = AppBadgeTone.Primary,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = categoryName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "단어 카드를 넘기며 소리를 들어보세요.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun WordLearningCard(
    state: LearningWordListUiState.Success,
    actions: WordLearningActions,
    modifier: Modifier = Modifier,
) {
    val word = state.currentWord ?: return

    Column(
        modifier = modifier.animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppCard(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            WordCardContent(
                word = word,
                audioState = state.audioState,
                onPlayAudio = actions.onPlayAudio,
                onStopAudio = actions.onStopAudio,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        WordNavigationControls(
            state = state,
            onPreviousClick = actions.onPreviousClick,
            onNextClick = actions.onNextClick,
            onStartQuiz = actions.onStartQuiz,
        )
    }
}

@Composable
private fun WordCardContent(
    word: LearningWord,
    audioState: AudioPlaybackState,
    onPlayAudio: () -> Unit,
    onStopAudio: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppNetworkImage(
            imageUrl = word.imageUrl,
            contentDescription = word.word,
            fallbackText = word.word,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
            placeholder = { WordFallback(word = word.word) },
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = word.word,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!word.displayText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = word.displayText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        AudioControlButton(
            audioState = audioState,
            onPlayClick = onPlayAudio,
            onStopClick = onStopAudio,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WordNavigationControls(
    state: LearningWordListUiState.Success,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onStartQuiz: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBadge(
            text = "${state.currentIndex + 1} / ${state.words.size}",
            tone = AppBadgeTone.Neutral,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSecondaryButton(
                text = "이전 단어",
                onClick = onPreviousClick,
                enabled = state.hasPrevious,
                modifier = Modifier.weight(1f),
            )
            AppPrimaryButton(
                text = if (state.hasNext) "다음 단어" else "퀴즈 풀기",
                onClick = if (state.hasNext) onNextClick else onStartQuiz,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WordFallback(
    word: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer.copy(
                                    alpha = 0.5f,
                                ),
                            ),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = word.firstOrNull()?.toString() ?: "",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Black,
            fontSize = 120.sp,
        )
    }
}
