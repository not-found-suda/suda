package com.ssafy.mobile.core.ui.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaStateView

private const val ENTER_SLIDE_DIVISOR = 4

/**
 * 데이터가 비어있을 때 보여주는 빈 화면 피드백 컴포넌트.
 */
@Composable
fun AppEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .animateContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = message.isNotBlank(),
            enter = fadeIn() + slideInVertically { it / ENTER_SLIDE_DIVISOR },
        ) {
            SudaStateView(
                mascot = SudaMascot.Empty,
                title = message,
            )
        }
    }
}
