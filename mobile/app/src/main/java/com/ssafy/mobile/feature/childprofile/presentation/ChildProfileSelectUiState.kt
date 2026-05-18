package com.ssafy.mobile.feature.childprofile.presentation

import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile

sealed interface ChildProfileSelectUiState {
    data object Loading : ChildProfileSelectUiState

    data class Success(
        val profiles: List<ChildProfile>,
        val activeChildId: Long? = null,
    ) : ChildProfileSelectUiState

    data object Empty : ChildProfileSelectUiState

    data class Error(
        val message: String,
    ) : ChildProfileSelectUiState
}
