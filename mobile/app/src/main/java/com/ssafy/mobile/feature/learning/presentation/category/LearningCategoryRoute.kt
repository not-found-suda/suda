@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.learning.presentation.category

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppInlineErrorText
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppNetworkImage
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.PreloadNetworkImages
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaMascotImage
import com.ssafy.mobile.core.ui.feedback.AppEmptyState
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY
import com.ssafy.mobile.feature.learning.domain.model.LearningCategory

@Composable
fun LearningCategoryRoute(
    onNavigateToWordList: (Long, String, String) -> Unit,
    onNavigateToQuiz: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LearningCategoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedDifficulty by remember { mutableStateOf(DEFAULT_LEARNING_DIFFICULTY) }

    LearningCategoryScreen(
        uiState = uiState,
        selectedDifficulty = selectedDifficulty,
        onDifficultyChange = { selectedDifficulty = it },
        onCategoryClick = { id, name -> onNavigateToWordList(id, name, selectedDifficulty) },
        onQuizClick = { id -> onNavigateToQuiz(id, selectedDifficulty) },
        onRetryClick = viewModel::loadCategories,
        modifier = modifier,
    )
}

@Composable
internal fun LearningCategoryScreen(
    uiState: LearningCategoryUiState,
    selectedDifficulty: String,
    onDifficultyChange: (String) -> Unit,
    onCategoryClick: (Long, String) -> Unit,
    onQuizClick: (Long) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LearningBackground()
            Column(modifier = Modifier.fillMaxSize()) {
                LearningMapHeader(
                    selectedDifficulty = selectedDifficulty,
                    onDifficultyChange = onDifficultyChange,
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (uiState) {
                        is LearningCategoryUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppLoadingIndicator(message = "학습 주제를 불러오고 있어요")
                            }
                        }

                        is LearningCategoryUiState.Success -> {
                            PreloadNetworkImages(
                                imageUrls = uiState.categories.map { it.thumbnailUrl },
                            )
                            LearningMap(
                                categories = uiState.categories,
                                onCategoryClick = onCategoryClick,
                                onQuizClick = onQuizClick,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        is LearningCategoryUiState.Empty -> {
                            AppEmptyState(message = "준비된 학습 주제가 없어요.")
                        }

                        is LearningCategoryUiState.Error -> {
                            LearningCategoryErrorState(
                                message = uiState.message,
                                onRetryClick = onRetryClick,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningBackground() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
                            ),
                    ),
                ),
    )
}

@Composable
private fun LearningMapHeader(
    selectedDifficulty: String,
    onDifficultyChange: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppBadge(
                    text = "오늘의 학습",
                    tone = AppBadgeTone.Success,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "어떤 주제로 떠나볼까요?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "단어를 익히고 바로 퀴즈로 별을 모아보세요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SudaMascotImage(
                mascot = SudaMascot.Default,
                contentDescription = null,
                modifier = Modifier.size(108.dp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        DifficultySelectionSection(
            selectedDifficulty = selectedDifficulty,
            onDifficultyChange = onDifficultyChange,
        )
    }
}

@Composable
private fun LearningMap(
    categories: List<LearningCategory>,
    onCategoryClick: (Long, String) -> Unit,
    onQuizClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(categories, key = { _, category -> category.categoryId }) { index, category ->
            LearningMapStep(
                category = category,
                stepNumber = index + 1,
                isCurrent = index == 0,
                alignEnd = index % 2 == 1,
                hasNext = index < categories.lastIndex,
                onWordListClick = { onCategoryClick(category.categoryId, category.name) },
                onQuizClick = { onQuizClick(category.categoryId) },
            )
        }
    }
}

@Composable
private fun LearningMapStep(
    category: LearningCategory,
    stepNumber: Int,
    isCurrent: Boolean,
    alignEnd: Boolean,
    hasNext: Boolean,
    onWordListClick: () -> Unit,
    onQuizClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        ) {
            LearningNodeCard(
                category = category,
                stepNumber = stepNumber,
                isCurrent = isCurrent,
                onWordListClick = onWordListClick,
                onQuizClick = onQuizClick,
                modifier = Modifier.fillMaxWidth(0.78f),
            )
        }
        if (hasNext) {
            LearningPathConnector(alignEnd = alignEnd)
        }
    }
}

@Composable
private fun LearningNodeCard(
    category: LearningCategory,
    stepNumber: Int,
    isCurrent: Boolean,
    onWordListClick: () -> Unit,
    onQuizClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "currentLearningNode")
    val currentScale by
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isCurrent) 1.035f else 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "currentLearningNodeScale",
        )

    Surface(
        modifier = modifier.scale(if (isCurrent) currentScale else 1f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isCurrent) 4.dp else 1.dp,
        shadowElevation = if (isCurrent) 10.dp else 3.dp,
        border =
            BorderStroke(
                width = if (isCurrent) 2.dp else 1.dp,
                color =
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CategoryThumbnail(
                    category = category,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppBadge(
                            text = if (isCurrent) "지금 추천" else "STEP $stepNumber",
                            tone = if (isCurrent) AppBadgeTone.Success else AppBadgeTone.Neutral,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!category.description.isNullOrBlank()) {
                        Text(
                            text = category.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LearningPillButton(
                    text = "단어 보기",
                    filled = false,
                    onClick = onWordListClick,
                    modifier = Modifier.weight(1f),
                )
                LearningPillButton(
                    text = "퀴즈 도전",
                    filled = true,
                    onClick = onQuizClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CategoryThumbnail(category: LearningCategory) {
    Box(
        modifier =
            Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.86f),
                            ),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        AppNetworkImage(
            imageUrl = category.thumbnailUrl,
            contentDescription = category.name,
            fallbackText = category.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
    }
}

@Composable
private fun LearningPathConnector(alignEnd: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.78f)
                    .height(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            repeat(3) { index ->
                Box(
                    modifier =
                        Modifier
                            .offset(y = ((index - 1) * 10).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)),
                )
            }
        }
    }
}

@Composable
private fun LearningPillButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(999.dp),
        color = if (filled) colors.primary else colors.primaryContainer.copy(alpha = 0.52f),
        contentColor = if (filled) colors.onPrimary else colors.onPrimaryContainer,
        shadowElevation = if (filled) 4.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DifficultySelectionSection(
    selectedDifficulty: String,
    onDifficultyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val difficulties =
        listOf(
            DifficultyOption("EASY", "쉬움", AppBadgeTone.Success),
            DifficultyOption("NORMAL", "보통", AppBadgeTone.Primary),
            DifficultyOption("HARD", "어려움", AppBadgeTone.Warning),
        )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "난이도 선택",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                difficulties.firstOrNull { it.value == selectedDifficulty }?.let { selected ->
                    AppBadge(text = selected.label, tone = selected.tone)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                difficulties.forEach { option ->
                    FilterChip(
                        selected = selectedDifficulty == option.value,
                        onClick = { onDifficultyChange(option.value) },
                        label = { Text(option.label) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LearningCategoryErrorState(
    message: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppInlineErrorText(text = message)
        Spacer(modifier = Modifier.height(16.dp))
        AppPrimaryButton(
            text = "다시 시도",
            onClick = onRetryClick,
        )
    }
}

private data class DifficultyOption(
    val value: String,
    val label: String,
    val tone: AppBadgeTone,
)
