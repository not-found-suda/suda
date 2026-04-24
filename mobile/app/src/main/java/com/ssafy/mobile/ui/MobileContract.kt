package com.ssafy.mobile.ui

data class MobileUiState(
    val message: String = "Android mobile base is ready.",
    val tapCount: Int = 0
)

sealed interface MobileIntent {
    data object TapPrimaryButton : MobileIntent
}

