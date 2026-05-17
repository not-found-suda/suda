package com.ssafy.mobile.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .animateContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        SudaStateView(
            mascot = SudaMascot.Loading,
            title = message ?: "준비하고 있어요",
            compact = true,
        )
    }
}
