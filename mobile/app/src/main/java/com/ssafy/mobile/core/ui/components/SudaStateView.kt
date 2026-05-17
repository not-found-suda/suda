@file:Suppress("FunctionNaming")

package com.ssafy.mobile.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SudaStateView(
    mascot: SudaMascot,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    compact: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val imageSize = if (compact) 52.dp else 104.dp
    val titleStyle =
        if (compact) {
            MaterialTheme.typography.bodyMedium
        } else {
            MaterialTheme.typography.titleMedium
        }
    val verticalGap = if (compact) 8.dp else 12.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SudaMascotImage(
            mascot = mascot,
            contentDescription = null,
            modifier = Modifier.size(imageSize),
        )
        Spacer(modifier = Modifier.height(verticalGap))
        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(if (compact) 10.dp else 14.dp))
            action()
        }
    }
}
