package com.ssafy.mobile.feature.childprofile.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.session.ActiveChildStorage
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
        private val activeChildStorage: ActiveChildStorage,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ChildProfileEditViewModel"
            private const val MAX_NAME_LENGTH = 20
            private const val MAX_AGE_LIMIT = 18
            private const val YEAR_START = 0
            private const val YEAR_END = 4
            private const val MONTH_START = 4
            private const val MONTH_END = 6
            private const val DAY_START = 6
            private const val DAY_END = 8
        }

        private val _uiState =
            MutableStateFlow<ChildProfileEditUiState>(
                ChildProfileEditUiState.Idle,
            )
        val uiState: StateFlow<ChildProfileEditUiState> = _uiState.asStateFlow()

        private val _isProfileLoaded = MutableStateFlow(false)
        val isProfileLoaded: StateFlow<Boolean> = _isProfileLoaded.asStateFlow()

        var name = MutableStateFlow("")
            private set

        var birthDate = MutableStateFlow("")
            private set

        private var editingChildId: Long? = null

        @Suppress("TooGenericExceptionCaught")
        fun loadProfile(childId: Long) {
            editingChildId = childId
            _isProfileLoaded.value = false
            _uiState.value = ChildProfileEditUiState.Loading
            viewModelScope.launch {
                try {
                    val profile =
                        withContext(Dispatchers.IO) {
                            childProfileRepository.getChildProfile(childId)
                        }
                    name.value = profile.name
                    birthDate.value = profile.birthDate
                    _isProfileLoaded.value = true
                    _uiState.value = ChildProfileEditUiState.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load child profile", e)
                    val message = e.message ?: "정보를 불러오지 못했습니다."
                    _uiState.value = ChildProfileEditUiState.Error(message)
                }
            }
        }

        fun saveProfile() {
            val isSaving = _uiState.value is ChildProfileEditUiState.Saving
            val isNotLoaded = editingChildId != null && !_isProfileLoaded.value
            if (isSaving || isNotLoaded) return

            val currentName = name.value.trim()
            val rawBirthDate = birthDate.value.trim()

            val validationResult = validateAndNormalizeBirthDate(rawBirthDate)
            val errorMessage =
                when {
                    currentName.isEmpty() -> "이름을 입력해 주세요."
                    currentName.length > MAX_NAME_LENGTH ->
                        "아이 이름은 ${MAX_NAME_LENGTH}자 이하로 입력해 주세요."
                    validationResult.error != null -> validationResult.error
                    else -> null
                }

            if (errorMessage != null) {
                _uiState.value = ChildProfileEditUiState.Error(errorMessage)
                return
            }

            val normalizedBirthDate = validationResult.normalizedDate!!
            performSave(currentName, normalizedBirthDate)
        }

        @Suppress("TooGenericExceptionCaught")
        fun deleteProfile() {
            val childId = editingChildId
            val isSaving = _uiState.value is ChildProfileEditUiState.Saving
            if (childId == null || isSaving || !_isProfileLoaded.value) return

            _uiState.value = ChildProfileEditUiState.Saving
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        childProfileRepository.deleteChildProfile(childId)

                        // API 성공 후, 현재 선택된 아이라면 클리어
                        if (activeChildStorage.getActiveChildId() == childId) {
                            activeChildStorage.clearActiveChildId()
                        }
                    }
                    _uiState.value = ChildProfileEditUiState.Deleted
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete child profile", e)
                    val message = e.message ?: "프로필 삭제 중 오류가 발생했습니다."
                    _uiState.value = ChildProfileEditUiState.Error(message)
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun performSave(
            currentName: String,
            normalizedBirthDate: String,
        ) {
            _uiState.value = ChildProfileEditUiState.Saving
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val childId = editingChildId
                        if (childId == null) {
                            childProfileRepository.createChildProfile(
                                name = currentName,
                                birthDate = normalizedBirthDate,
                            )
                        } else {
                            childProfileRepository.updateChildProfile(
                                childId = childId,
                                name = currentName,
                                birthDate = normalizedBirthDate,
                            )
                        }
                    }
                    _uiState.value = ChildProfileEditUiState.Success
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save child profile", e)
                    val message = e.message ?: "아이 정보를 저장하지 못했습니다."
                    _uiState.value = ChildProfileEditUiState.Error(message)
                }
            }
        }

        fun onNameChange(newName: String) {
            name.value = newName
            if (_uiState.value is ChildProfileEditUiState.Error) {
                _uiState.value = ChildProfileEditUiState.Idle
            }
        }

        fun onBirthDateChange(newBirthDate: String) {
            if (newBirthDate.isEmpty() || newBirthDate.all { it.isDigit() || it == '-' }) {
                birthDate.value = newBirthDate
            }
            if (_uiState.value is ChildProfileEditUiState.Error) {
                _uiState.value = ChildProfileEditUiState.Idle
            }
        }

        private fun validateAndNormalizeBirthDate(raw: String): BirthDateValidationResult {
            val normalized =
                when {
                    raw.matches(Regex("""^\d{4}-\d{2}-\d{2}$""")) -> raw
                    raw.matches(Regex("""^\d{8}$""")) -> {
                        val y = raw.substring(YEAR_START, YEAR_END)
                        val m = raw.substring(MONTH_START, MONTH_END)
                        val d = raw.substring(DAY_START, DAY_END)
                        "$y-$m-$d"
                    }
                    else ->
                        return BirthDateValidationResult(
                            error = "생년월일 형식이 올바르지 않습니다. (예: 2020-05-01)",
                        )
                }

            return try {
                val date = java.time.LocalDate.parse(normalized)
                val now = java.time.LocalDate.now()

                when {
                    date.isAfter(now) -> BirthDateValidationResult(error = "미래 날짜는 입력할 수 없습니다.")
                    java.time.Period
                        .between(date, now)
                        .years > MAX_AGE_LIMIT ->
                        BirthDateValidationResult(error = "나이는 0세에서 ${MAX_AGE_LIMIT}세 사이여야 합니다.")
                    else -> BirthDateValidationResult(normalizedDate = normalized)
                }
            } catch (e: java.time.format.DateTimeParseException) {
                BirthDateValidationResult(error = "유효하지 않은 날짜입니다.")
            }
        }

        private data class BirthDateValidationResult(
            val normalizedDate: String? = null,
            val error: String? = null,
        )
    }
