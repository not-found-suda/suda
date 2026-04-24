package com.ssafy.mobile.feature.sample.presentation

data class SampleUiState(
    val statusMessage: String = "Tap the button to call sample API and sync local storage.",
    val isLoading: Boolean = false,
    val todoTitle: String = "No local data yet.",
    val isCompleted: Boolean? = null,
    val lastSyncedTodoId: Int? = null,
    val lastSyncedAt: String = "-",
    val errorMessage: String? = null,
)

sealed interface SampleIntent {
    data object FetchSampleData : SampleIntent

    data object ClearError : SampleIntent
}
