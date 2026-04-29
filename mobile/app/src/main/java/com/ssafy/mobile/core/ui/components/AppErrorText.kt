package com.ssafy.mobile.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 에러 상태를 표시하기 위한 전용 텍스트 컴포넌트.
 */
@Composable
fun AppErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier.padding(vertical = 4.dp),
        )
    }
}
