package com.ssafy.mobile.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PreloadNetworkImages(imageUrls: List<String?>) {
    val context = LocalContext.current
    val urls =
        remember(imageUrls) {
            imageUrls
                .asSequence()
                .filterNotNull()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
        }

    LaunchedEffect(urls) {
        urls.forEach { url ->
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .diskCacheKey(url)
                    .build()
            context.imageLoader.enqueue(request)
        }
    }
}

@Composable
fun rememberNetworkImagesPreloaded(imageUrls: List<String?>): Boolean {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val urls =
        remember(imageUrls) {
            imageUrls
                .asSequence()
                .filterNotNull()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
        }
    val isPreloaded by
        produceState(
            initialValue = urls.isEmpty(),
            key1 = urls,
            key2 = imageLoader,
        ) {
            value = urls.isEmpty()
            if (urls.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    urls.forEach { url ->
                        runCatching {
                            imageLoader.execute(context.preloadImageRequest(url))
                        }
                    }
                }
                value = true
            }
        }

    return isPreloaded
}

private fun android.content.Context.preloadImageRequest(url: String): ImageRequest =
    ImageRequest
        .Builder(this)
        .data(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .diskCacheKey(url)
        .build()
