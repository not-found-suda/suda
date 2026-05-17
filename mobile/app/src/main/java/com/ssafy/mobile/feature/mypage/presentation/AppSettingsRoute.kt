@file:Suppress("MagicNumber")

package com.ssafy.mobile.feature.mypage.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.theme.AppThemeMode

@Composable
fun AppSettingsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    AppSettingsScreen(
        themeMode = themeMode,
        onThemeModeSelected = viewModel::updateThemeMode,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "앱 설정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(text = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "화면과 사용 환경을 설정해요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThemeModeSettingsGroup(
                selectedMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
            )
        }
    }
}

@Composable
private fun ThemeModeSettingsGroup(
    selectedMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            AppThemeMode.entries.forEachIndexed { index, mode ->
                ThemeModeRow(
                    mode = mode,
                    selected = mode == selectedMode,
                    onClick = { onThemeModeSelected(mode) },
                )
                if (index != AppThemeMode.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeRow(
    mode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ).clickable(onClick = onClick)
                .padding(start = 20.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = mode.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
    }
}
