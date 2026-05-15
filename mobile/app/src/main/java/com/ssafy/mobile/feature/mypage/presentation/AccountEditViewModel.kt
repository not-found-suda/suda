package com.ssafy.mobile.feature.mypage.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.mypage.data.repository.AccountRepository
import com.ssafy.mobile.feature.mypage.domain.model.AccountInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccountEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val accountInfo: AccountInfo? = null,
    val nameError: String? = null,
    val errorMessage: String? = null,
    val eventMessage: String? = null,
)

@HiltViewModel
class AccountEditViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AccountEditUiState())
        val uiState: StateFlow<AccountEditUiState> = _uiState.asStateFlow()

        private val _name = MutableStateFlow("")
        val name: StateFlow<String> = _name.asStateFlow()

        init {
            loadAccountInfo()
        }

        fun loadAccountInfo() {
            if (_uiState.value.isSaving) return

            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null,
                    nameError = null,
                    eventMessage = null,
                )

            viewModelScope.launch {
                try {
                    val accountInfo =
                        withContext(Dispatchers.IO) {
                            accountRepository.getAccountInfo()
                        }
                    _name.value = accountInfo.name
                    _uiState.value =
                        AccountEditUiState(
                            isLoading = false,
                            accountInfo = accountInfo,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Log.e(TAG, "Failed to load account info", e)
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "계정 정보를 불러오지 못했습니다.",
                        )
                }
            }
        }

        fun onNameChange(value: String) {
            _name.value = value
            if (_uiState.value.nameError != null || _uiState.value.errorMessage != null) {
                _uiState.value =
                    _uiState.value.copy(
                        nameError = null,
                        errorMessage = null,
                    )
            }
        }

        fun saveAccountInfo() {
            val currentState = _uiState.value
            if (currentState.isLoading || currentState.isSaving) return

            val currentAccount = currentState.accountInfo
            val normalizedName = _name.value.trim()
            val validationMessage = validateName(normalizedName)

            when {
                validationMessage != null -> {
                    _uiState.value = currentState.copy(nameError = validationMessage)
                }

                currentAccount != null && normalizedName == currentAccount.name -> {
                    _uiState.value = currentState.copy(eventMessage = "변경된 내용이 없습니다.")
                }

                else -> {
                    performSave(
                        currentState = currentState,
                        currentAccount = currentAccount,
                        normalizedName = normalizedName,
                    )
                }
            }
        }

        private fun performSave(
            currentState: AccountEditUiState,
            currentAccount: AccountInfo?,
            normalizedName: String,
        ) {
            _uiState.value =
                currentState.copy(
                    isSaving = true,
                    nameError = null,
                    errorMessage = null,
                    eventMessage = null,
                )

            viewModelScope.launch {
                saveAccountInfoInternal(
                    currentAccount = currentAccount,
                    normalizedName = normalizedName,
                )
            }
        }

        fun clearEventMessage() {
            if (_uiState.value.eventMessage != null) {
                _uiState.value = _uiState.value.copy(eventMessage = null)
            }
        }

        private fun validateName(name: String): String? =
            when {
                name.isBlank() -> "이름을 입력해 주세요."
                name.length > MAX_NAME_LENGTH -> "이름은 ${MAX_NAME_LENGTH}자 이하로 입력해 주세요."
                else -> null
            }

        private suspend fun saveAccountInfoInternal(
            currentAccount: AccountInfo?,
            normalizedName: String,
        ) {
            try {
                val updateResult =
                    withContext(Dispatchers.IO) {
                        accountRepository.updateName(normalizedName)
                    }
                val updatedAccount =
                    AccountInfo(
                        userId = updateResult.userId,
                        email = updateResult.email,
                        name = updateResult.name,
                        active = updateResult.active,
                        role = updateResult.role,
                        ttsSpeaker = currentAccount?.ttsSpeaker,
                    )
                _name.value = updateResult.name
                _uiState.value =
                    AccountEditUiState(
                        isLoading = false,
                        isSaving = false,
                        accountInfo = updatedAccount,
                        eventMessage = "계정 정보를 저장했어요.",
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.e(TAG, "Failed to save account info", e)
                _uiState.value =
                    _uiState.value.copy(
                        isSaving = false,
                        errorMessage = e.message ?: "계정 정보를 저장하지 못했습니다.",
                    )
            }
        }

        private companion object {
            const val TAG = "AccountEditViewModel"
            const val MAX_NAME_LENGTH = 50
        }
    }
