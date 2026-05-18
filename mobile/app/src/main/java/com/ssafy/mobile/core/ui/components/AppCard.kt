package com.ssafy.mobile.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val border =
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        )
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)

    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}
