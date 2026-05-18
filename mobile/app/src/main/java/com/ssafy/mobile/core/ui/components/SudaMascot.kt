@file:Suppress("FunctionNaming")

package com.ssafy.mobile.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ssafy.mobile.R

enum class SudaMascot(
    @param:DrawableRes val resId: Int,
) {
    Default(R.drawable.mascot_default),
    Icon(R.drawable.mascot_icon),
    WordCard(R.drawable.mascot_word_card),
    Microphone(R.drawable.mascot_microphone),
    Success3Star(R.drawable.mascot_success_3star),
    Good2Star(R.drawable.mascot_good_2star),
    Retry1Star(R.drawable.mascot_retry_1star),
    Loading(R.drawable.mascot_loading),
    Empty(R.drawable.mascot_empty),
    ErrorNetwork(R.drawable.mascot_error_network),
    Report(R.drawable.mascot_report),
    Sleep(R.drawable.mascot_sleep),
}

@Composable
fun SudaMascotImage(
    mascot: SudaMascot,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        painter = painterResource(id = mascot.resId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
