package com.ssafy.mobile.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.SubcomposeAsyncImage

/**
 * 네트워크 이미지를 로딩하고 실패 시 대체 UI를 표시하는 공통 컴포넌트입니다.
 */
@Composable
fun AppNetworkImage(
    imageUrl: String?,
    contentDescription: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val fallback =
        placeholder ?: {
            DefaultFallback(fallbackText = fallbackText)
        }

    if (imageUrl.isNullOrBlank()) {
        Box(modifier = modifier) {
            fallback()
        }
    } else {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

@Composable
private fun DefaultFallback(
    fallbackText: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fallbackText.firstOrNull()?.toString() ?: "",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}
