@file:Suppress("MagicNumber")

package com.ssafy.mobile.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

@Composable
fun FlipCard(
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isFlipped: Boolean = false,
    onFlip: () -> Unit = {},
) {
    val density = LocalDensity.current

    // 0도에서 180도까지 부드럽게 회전하는 애니메이션
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "flip",
    )

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density.density // 3D 원근감 부여 (필수!)
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFlip,
                ),
    ) {
        // 카드가 90도 이상 회전하면 뒷면, 아니면 앞면을 보여줌
        if (rotation <= 90f) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                front()
            }
        } else {
            // 뒷면: 글자가 뒤집혀 보이지 않도록 rotationY = 180f로 다시 보정
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f },
            ) {
                back()
            }
        }
    }
}
