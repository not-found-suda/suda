package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaStateView

@Composable
internal fun ReportQuizSessionDetailStatusCard(message: String) {
    val lines = message.toStateLines()
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SudaStateView(
            mascot = if (message.contains("없")) SudaMascot.Empty else SudaMascot.Loading,
            title = lines.first,
            description = lines.second,
            modifier = Modifier.height(132.dp),
            compact = true,
        )
    }
}

@Composable
internal fun ReportQuizSessionDetailErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SudaStateView(
            mascot = SudaMascot.ErrorNetwork,
            title = "퀴즈 상세를 불러오지 못했어요",
            description = message,
            action = {
                AppSecondaryButton(
                    text = "다시 시도",
                    onClick = onRetryClick,
                    modifier = Modifier.height(36.dp),
                )
            },
        )
    }
}

@Composable
internal fun ReportQuizSessionDetailActionCard(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SudaStateView(
            mascot = SudaMascot.Report,
            title = message,
            action = {
                AppPrimaryButton(
                    text = buttonText,
                    onClick = onClick,
                    modifier = Modifier.height(40.dp),
                )
            },
        )
    }
}

private fun String.toStateLines(): Pair<String, String?> {
    val lines = lines().filter { it.isNotBlank() }
    return lines.firstOrNull().orEmpty() to
        lines.drop(1).joinToString("\n").takeIf { it.isNotBlank() }
}
