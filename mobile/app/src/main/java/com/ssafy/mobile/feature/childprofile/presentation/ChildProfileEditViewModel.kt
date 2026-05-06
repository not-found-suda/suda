package com.ssafy.mobile.feature.childprofile.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.repository.ChildProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChildProfileEditViewModel
    @Inject
    constructor(
        private val childProfileRepository: ChildProfileRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ChildProfileEditViewModel"
            private const val MAX_AGE = 18
        }

        private val _uiState =
            MutableStateFlow<ChildProfileEditUiState>(ChildProfileEditUiState.Idle)
        val uiState: StateFlow<ChildProfileEditUiState> = _uiState.asStateFlow()

        var name = MutableStateFlow("")
            private set

        var age = MutableStateFlow("")
            private set

        @Suppress("TooGenericExceptionCaught")
        fun saveProfile() {
            if (_uiState.value is ChildProfileEditUiState.Saving) return

            val currentName = name.value.trim()
            val currentAgeText = age.value.trim()
            val currentAge = currentAgeText.toIntOrNull()

            val errorMessage =
                when {
                    currentName.isEmpty() -> "이름을 입력해 주세요."
                    currentAge == null -> "나이를 숫자로 입력해 주세요."
                    currentAge !in 0..MAX_AGE -> "나이는 0세에서 ${MAX_AGE}세 사이여야 합니다."
                    else -> null
                }

            if (errorMessage != null) {
                _uiState.value = ChildProfileEditUiState.Error(errorMessage)
                return
            }

            _uiState.value = ChildProfileEditUiState.Saving
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        childProfileRepository.createChildProfile(
                            name = currentName,
                            age = currentAge!!,
                        )
                    }
                    _uiState.value = ChildProfileEditUiState.Success
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save child profile", e)
                    _uiState.value = ChildProfileEditUiState.Error("아이 정보를 저장하지 못했습니다.")
                }
            }
        }

        fun onNameChange(newName: String) {
            name.value = newName
            if (_uiState.value is ChildProfileEditUiState.Error) {
                _uiState.value = ChildProfileEditUiState.Idle
            }
        }

        fun onAgeChange(newAge: String) {
            // 숫자만 허용
            if (newAge.isEmpty() || newAge.all { it.isDigit() }) {
                age.value = newAge
            }
            if (_uiState.value is ChildProfileEditUiState.Error) {
                _uiState.value = ChildProfileEditUiState.Idle
            }
        }
    }
