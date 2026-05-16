package com.ssafy.mobile.feature.learning.presentation.category

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppInlineErrorText
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppNetworkImage
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.feedback.AppEmptyState
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY
import com.ssafy.mobile.feature.learning.domain.model.LearningCategory

private const val GRID_COLUMNS = 2
private val GRID_HORIZONTAL_PADDING = 20.dp
private val GRID_VERTICAL_PADDING = 8.dp
private val GRID_SPACING = 16.dp
private val HEADER_HORIZONTAL_PADDING = 24.dp
private val HEADER_VERTICAL_PADDING = 32.dp
private val CARD_PADDING = 14.dp
private const val ASPECT_RATIO_SQUARE = 1f
private val ACTION_BUTTON_MIN_HEIGHT = 44.dp

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
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            HeaderSection()

            DifficultySelectionSection(
                selectedDifficulty = selectedDifficulty,
                onDifficultyChange = onDifficultyChange,
                modifier = Modifier.padding(horizontal = HEADER_HORIZONTAL_PADDING),
            )

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier.weight(1f),
            ) {
                when (uiState) {
                    is LearningCategoryUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppLoadingIndicator(message = "학습 주제를 불러오고 있어요.")
                        }
                    }

                    is LearningCategoryUiState.Success -> {
                        CategoryGrid(
                            categories = uiState.categories,
                            onCategoryClick = onCategoryClick,
                            onQuizClick = onQuizClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is LearningCategoryUiState.Empty -> {
                        AppEmptyState(message = "준비된 학습 주제가 없습니다.")
                    }

                    is LearningCategoryUiState.Error -> {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(HEADER_HORIZONTAL_PADDING),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AppInlineErrorText(text = uiState.message)
                            Spacer(modifier = Modifier.height(16.dp))
                            AppPrimaryButton(
                                text = "다시 시도",
                                onClick = onRetryClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(
                    horizontal = HEADER_HORIZONTAL_PADDING,
                    vertical = HEADER_VERTICAL_PADDING,
                ),
    ) {
        AppBadge(
            text = "학습",
            tone = AppBadgeTone.Primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "어떤 주제로 배워볼까요?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "오늘 배울 단어 주제를 골라 주세요.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryGrid(
    categories: List<LearningCategory>,
    onCategoryClick: (Long, String) -> Unit,
    onQuizClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding =
            PaddingValues(
                horizontal = GRID_HORIZONTAL_PADDING,
                vertical = GRID_VERTICAL_PADDING,
            ),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        modifier = modifier,
    ) {
        items(categories, key = { it.categoryId }) { category ->
            CategoryCard(
                category = category,
                onWordListClick = { onCategoryClick(category.categoryId, category.name) },
                onQuizClick = { onQuizClick(category.categoryId) },
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: LearningCategory,
    onWordListClick: () -> Unit,
    onQuizClick: () -> Unit,
) {
    AppCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(),
        contentPadding = PaddingValues(CARD_PADDING),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppNetworkImage(
                imageUrl = category.thumbnailUrl,
                contentDescription = category.name,
                fallbackText = category.name,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(ASPECT_RATIO_SQUARE)
                        .clip(RoundedCornerShape(8.dp)),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            if (!category.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LearningCategoryActionButton(
                    text = "단어장",
                    tone = LearningCategoryActionTone.Soft,
                    onClick = onWordListClick,
                )
                LearningCategoryActionButton(
                    text = "퀴즈",
                    tone = LearningCategoryActionTone.Filled,
                    onClick = onQuizClick,
                )
            }
        }
    }
}

@Composable
private fun LearningCategoryActionButton(
    text: String,
    tone: LearningCategoryActionTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isFilled = tone == LearningCategoryActionTone.Filled
    val containerColor =
        if (isFilled) {
            colors.primary
        } else {
            colors.primaryContainer.copy(alpha = 0.42f)
        }
    val contentColor =
        if (isFilled) {
            colors.onPrimary
        } else {
            colors.primary
        }

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ACTION_BUTTON_MIN_HEIGHT)
                .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (isFilled) 2.dp else 0.dp,
        shadowElevation = if (isFilled) 3.dp else 0.dp,
        border =
            if (isFilled) {
                null
            } else {
                BorderStroke(1.dp, colors.primary.copy(alpha = 0.18f))
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
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

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
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
            difficulties
                .firstOrNull { it.value == selectedDifficulty }
                ?.let { selected ->
                    AppBadge(
                        text = selected.label,
                        tone = selected.tone,
                    )
                }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            difficulties.forEach { option ->
                FilterChip(
                    selected = selectedDifficulty == option.value,
                    onClick = { onDifficultyChange(option.value) },
                    label = { Text(option.label) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
            }
        }
    }
}

private data class DifficultyOption(
    val value: String,
    val label: String,
    val tone: AppBadgeTone,
)

private enum class LearningCategoryActionTone {
    Soft,
    Filled,
}
