package com.ssafy.mobile.feature.childprofile.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.session.ActiveChildStorage
import com.ssafy.mobile.feature.childprofile.domain.repository.ChildProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChildProfileSelectViewModel
    @Inject
    constructor(
        private val childProfileRepository: ChildProfileRepository,
        private val activeChildStorage: ActiveChildStorage,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ChildProfileSelectViewModel"
        }

        private val _uiState =
            MutableStateFlow<ChildProfileSelectUiState>(ChildProfileSelectUiState.Loading)
        val uiState: StateFlow<ChildProfileSelectUiState> = _uiState.asStateFlow()

        private val _isSelecting = MutableStateFlow(false)
        val isSelecting: StateFlow<Boolean> = _isSelecting.asStateFlow()

        private val _navigationEvent = MutableSharedFlow<ChildProfileSelectNavigationEvent>()
        val navigationEvent: SharedFlow<ChildProfileSelectNavigationEvent> =
            _navigationEvent
                .asSharedFlow()

        init {
            loadProfiles()
        }

        @Suppress("TooGenericExceptionCaught")
        fun loadProfiles() {
            _uiState.value = ChildProfileSelectUiState.Loading
            viewModelScope.launch {
                try {
                    val profiles =
                        withContext(Dispatchers.IO) {
                            childProfileRepository.getChildProfiles()
                        }
                    val activeChildId =
                        withContext(Dispatchers.IO) {
                            activeChildStorage.getActiveChildId()
                        }

                    _uiState.value =
                        if (profiles.isEmpty()) {
                            ChildProfileSelectUiState.Empty
                        } else {
                            ChildProfileSelectUiState.Success(
                                profiles = profiles,
                                activeChildId = activeChildId,
                            )
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to load child profiles", e)
                    _uiState.value = ChildProfileSelectUiState.Error("네트워크 연결을 확인해 주세요.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load child profiles", e)
                    _uiState.value = ChildProfileSelectUiState.Error("아이 프로필 목록을 불러오지 못했습니다.")
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun selectProfile(childId: Long) {
            if (_isSelecting.value) return
            _isSelecting.value = true

            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        activeChildStorage.saveActiveChildId(childId)
                    }
                    _navigationEvent.emit(ChildProfileSelectNavigationEvent.NavigateToHome)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save active child id", e)
                    _isSelecting.value = false
                    _uiState.value = ChildProfileSelectUiState.Error("프로필 선택 정보를 저장하지 못했습니다.")
                }
            }
        }

        fun retry() {
            loadProfiles()
        }
    }

sealed interface ChildProfileSelectNavigationEvent {
    data object NavigateToHome : ChildProfileSelectNavigationEvent
}
