package com.ssafy.mobile.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

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
        val context = LocalContext.current
        val request =
            remember(context, imageUrl) {
                ImageRequest
                    .Builder(context)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .diskCacheKey(imageUrl)
                    .build()
            }

        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            loading = { DefaultLoadingPlaceholder() },
            error = { fallback() },
        )
    }
}

@Composable
private fun DefaultLoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        SudaMascotImage(
            mascot = SudaMascot.Loading,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
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
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        SudaMascotImage(
            mascot = SudaMascot.Empty,
            contentDescription = fallbackText.ifBlank { null },
            modifier = Modifier.size(44.dp),
        )
    }
}
