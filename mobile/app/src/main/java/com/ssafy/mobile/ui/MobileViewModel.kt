package com.ssafy.mobile.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MobileViewModel : ViewModel() {
    private val initialMessage = "Android mobile base is ready."
    private val changedMessage = "State changed from ViewModel."

    private val _uiState = MutableStateFlow(MobileUiState())
    val uiState: StateFlow<MobileUiState> = _uiState.asStateFlow()

    fun onIntent(intent: MobileIntent) {
        when (intent) {
            MobileIntent.TapPrimaryButton -> {
                _uiState.update { current ->
                    val nextTapCount = current.tapCount + 1
                    current.copy(
                        message = if (nextTapCount % 2 == 0) initialMessage else changedMessage,
                        tapCount = nextTapCount
                    )
                }
            }
        }
    }
}

