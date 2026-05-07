package com.ssafy.mobile.feature.learning.presentation.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppErrorText
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppNetworkImage
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.feature.learning.domain.model.LearningCategory

private const val GRID_COLUMNS = 2
private val GRID_HORIZONTAL_PADDING = 20.dp
private val GRID_VERTICAL_PADDING = 8.dp
private val GRID_SPACING = 16.dp
private val HEADER_HORIZONTAL_PADDING = 24.dp
private val HEADER_VERTICAL_PADDING = 32.dp
private val CARD_CORNER_RADIUS = 16.dp
private val CARD_PADDING = 12.dp
private val CARD_ELEVATION = 2.dp
private const val ASPECT_RATIO_SQUARE = 1f

@Composable
fun LearningCategoryRoute(
    onNavigateToWordList: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LearningCategoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LearningCategoryScreen(
        uiState = uiState,
        onCategoryClick = onNavigateToWordList,
        onRetryClick = viewModel::loadCategories,
        modifier = modifier,
    )
}

@Composable
internal fun LearningCategoryScreen(
    uiState: LearningCategoryUiState,
    onCategoryClick: (Long, String) -> Unit,
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

            Box(
                modifier = Modifier.weight(1f),
            ) {
                when (uiState) {
                    is LearningCategoryUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppLoadingIndicator()
                        }
                    }

                    is LearningCategoryUiState.Success -> {
                        CategoryGrid(
                            categories = uiState.categories,
                            onCategoryClick = onCategoryClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is LearningCategoryUiState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "준비된 학습 주제가 없습니다.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                            AppErrorText(text = uiState.message)
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
                .padding(
                    horizontal = HEADER_HORIZONTAL_PADDING,
                    vertical = HEADER_VERTICAL_PADDING,
                ),
    ) {
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
                onClick = { onCategoryClick(category.categoryId, category.name) },
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: LearningCategory,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = CARD_ELEVATION),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CARD_PADDING),
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
                        .clip(RoundedCornerShape(12.dp)),
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
        }
    }
}
