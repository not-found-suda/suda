package com.ssafy.mobile.feature.childprofile.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile

@Composable
fun ChildProfileSelectRoute(
    navController: NavController,
    onNavigateToHome: () -> Unit,
    onNavigateToCreate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildProfileSelectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSelecting by viewModel.isSelecting.collectAsStateWithLifecycle()

    // 이전 화면에서 전달된 변경 플래그 확인
    val refreshNeeded by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("child_profile_changed", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    LaunchedEffect(refreshNeeded) {
        if (refreshNeeded) {
            viewModel.loadProfiles()
            navController.currentBackStackEntry?.savedStateHandle?.set(
                "child_profile_changed",
                false,
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                ChildProfileSelectNavigationEvent.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    ChildProfileSelectScreen(
        uiState = uiState,
        isSelecting = isSelecting,
        onProfileSelect = viewModel::selectProfile,
        onRetry = viewModel::retry,
        onNavigateToCreate = onNavigateToCreate,
        modifier = modifier,
    )
}

@Composable
private fun ChildProfileSelectScreen(
    uiState: ChildProfileSelectUiState,
    isSelecting: Boolean,
    onProfileSelect: (Long) -> Unit,
    onRetry: () -> Unit,
    onNavigateToCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "아이 선택",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "학습을 진행할 아이를 선택해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (uiState) {
            is ChildProfileSelectUiState.Loading -> {
                LoadingContent()
            }
            is ChildProfileSelectUiState.Success -> {
                ProfileList(
                    profiles = uiState.profiles,
                    activeChildId = uiState.activeChildId,
                    isSelecting = isSelecting,
                    onProfileSelect = onProfileSelect,
                    onNavigateToCreate = onNavigateToCreate,
                )
            }
            is ChildProfileSelectUiState.Empty -> {
                EmptyContent(onNavigateToCreate = onNavigateToCreate)
            }
            is ChildProfileSelectUiState.Error -> {
                ErrorContent(
                    message = uiState.message,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "목록을 불러오는 중입니다...")
    }
}

@Composable
private fun ProfileList(
    profiles: List<ChildProfile>,
    activeChildId: Long?,
    isSelecting: Boolean,
    onProfileSelect: (Long) -> Unit,
    onNavigateToCreate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(profiles) { profile ->
            ProfileItem(
                profile = profile,
                isSelected = profile.childId == activeChildId,
                enabled = !isSelecting,
                onClick = { onProfileSelect(profile.childId) },
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onNavigateToCreate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSelecting,
            ) {
                Text(text = "+ 아이 추가하기")
            }
        }
    }
}

@Composable
private fun ProfileItem(
    profile: ChildProfile,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = profile.name.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (profile.age != null) {
                    Text(
                        text = "${profile.age}세",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isSelected) {
                Text(
                    text = "현재 선택됨",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(onNavigateToCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "등록된 아이가 없습니다.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateToCreate) {
            Text(text = "아이 프로필 만들기")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text(text = "다시 시도")
        }
    }
}
