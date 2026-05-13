package com.ssafy.mobile.feature.learning.presentation.wordlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone

@Composable
internal fun AudioControlButton(
    audioState: AudioPlaybackState,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = audioControlColors(audioState)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = {
                if (audioState == AudioPlaybackState.Playing) {
                    onStopClick()
                } else {
                    onPlayClick()
                }
            },
            enabled = audioState != AudioPlaybackState.Loading,
            shape = CircleShape,
            color = colors.container,
            contentColor = colors.content,
            modifier = Modifier.size(72.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AudioControlIcon(audioState = audioState, contentColor = colors.content)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AppBadge(
            text = audioState.toAudioLabel(),
            tone = audioState.toAudioBadgeTone(),
        )
    }
}

@Composable
private fun AudioControlIcon(
    audioState: AudioPlaybackState,
    contentColor: Color,
) {
    if (audioState == AudioPlaybackState.Loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = contentColor,
            strokeWidth = 3.dp,
        )
    } else {
        Text(
            text = if (audioState == AudioPlaybackState.Playing) "■" else "▶",
            fontSize = 32.sp,
            color = contentColor,
        )
    }
}

@Composable
private fun audioControlColors(audioState: AudioPlaybackState): AudioControlColors =
    when (audioState) {
        AudioPlaybackState.Playing ->
            AudioControlColors(
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
            )
        AudioPlaybackState.Error ->
            AudioControlColors(
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        else ->
            AudioControlColors(
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
    }

private fun AudioPlaybackState.toAudioLabel(): String =
    when (this) {
        AudioPlaybackState.Loading -> "준비 중"
        AudioPlaybackState.Playing -> "재생 중"
        AudioPlaybackState.Error -> "재생 실패"
        else -> "소리 듣기"
    }

private fun AudioPlaybackState.toAudioBadgeTone(): AppBadgeTone =
    when (this) {
        AudioPlaybackState.Error -> AppBadgeTone.Error
        AudioPlaybackState.Playing -> AppBadgeTone.Primary
        else -> AppBadgeTone.Neutral
    }

private data class AudioControlColors(
    val container: Color,
    val content: Color,
)
