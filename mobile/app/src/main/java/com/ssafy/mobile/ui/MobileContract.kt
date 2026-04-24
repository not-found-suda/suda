package com.ssafy.mobile.ui

data class MobileUiState(
    val statusMessage: String = "Tap the button to call sample API and sync local storage.",
    val isLoading: Boolean = false,
    val todoTitle: String = "No local data yet.",
    val isCompleted: Boolean? = null,
    val lastSyncedTodoId: Int? = null,
    val lastSyncedAt: String = "-",
    val errorMessage: String? = null
)

sealed interface MobileIntent {
    data object FetchSampleData : MobileIntent
    data object ClearError : MobileIntent
}
