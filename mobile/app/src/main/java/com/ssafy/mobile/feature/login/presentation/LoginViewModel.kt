package com.ssafy.mobile.feature.login.presentation

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.auth.TokenStorage
import com.ssafy.mobile.core.session.ActiveChildStorage
import com.ssafy.mobile.feature.login.data.repository.LoginException
import com.ssafy.mobile.feature.login.data.repository.LoginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val loginRepository: LoginRepository,
        private val tokenStorage: TokenStorage,
        private val activeChildStorage: ActiveChildStorage,
    ) : ViewModel() {
        companion object {
            private const val TAG = "LoginViewModel"
        }

        private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
        val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

        private val _email = MutableStateFlow("")
        val email: StateFlow<String> = _email.asStateFlow()

        private val _password = MutableStateFlow("")
        val password: StateFlow<String> = _password.asStateFlow()

        private val _emailError = MutableStateFlow<String?>(null)
        val emailError: StateFlow<String?> = _emailError.asStateFlow()

        private val _passwordError = MutableStateFlow<String?>(null)
        val passwordError: StateFlow<String?> = _passwordError.asStateFlow()

        fun onEmailChanged(value: String) {
            _email.value = value
            _emailError.value = null
        }

        fun onPasswordChanged(value: String) {
            _password.value = value
            _passwordError.value = null
        }

        fun login() {
            if (!validateInputs()) return

            _uiState.value = LoginUiState.Loading

            viewModelScope.launch {
                try {
                    val response =
                        withContext(Dispatchers.IO) {
                            loginRepository.login(_email.value.trim(), _password.value)
                        }

                    try {
                        withContext(Dispatchers.IO) {
                            tokenStorage.clearTokens()
                            tokenStorage.updateAccessToken(response.accessToken)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: GeneralSecurityException) {
                        Log.e(TAG, "Token storage failed")
                        _uiState.value =
                            LoginUiState.Error(
                                message = "로그인 정보를 저장하지 못했습니다. 다시 시도해 주세요.",
                            )
                        return@launch
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Token storage failed")
                        _uiState.value =
                            LoginUiState.Error(
                                message = "로그인 정보를 저장하지 못했습니다. 다시 시도해 주세요.",
                            )
                        return@launch
                    }

                    val hasActiveChild =
                        withContext(Dispatchers.IO) {
                            activeChildStorage.getActiveChildId() != null
                        }

                    _uiState.value = LoginUiState.Success(hasActiveChild = hasActiveChild)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LoginException) {
                    _uiState.value =
                        LoginUiState.Error(
                            message = e.message ?: "로그인에 실패했습니다.",
                        )
                } catch (e: IOException) {
                    Log.e(TAG, "Login network error", e)
                    _uiState.value =
                        LoginUiState.Error(
                            message = "네트워크 연결을 확인해 주세요.",
                        )
                } catch (e: IllegalStateException) {
                    _uiState.value =
                        LoginUiState.Error(
                            message = e.message ?: "알 수 없는 오류가 발생했습니다.",
                        )
                }
            }
        }

        fun clearError() {
            if (_uiState.value is LoginUiState.Error) {
                _uiState.value = LoginUiState.Idle
            }
        }

        private fun validateInputs(): Boolean {
            var isValid = true

            val emailValue = _email.value.trim()
            if (emailValue.isEmpty()) {
                _emailError.value = "이메일을 입력해 주세요."
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
                _emailError.value = "올바른 이메일 형식을 입력해 주세요."
                isValid = false
            }

            if (_password.value.isEmpty()) {
                _passwordError.value = "비밀번호를 입력해 주세요."
                isValid = false
            }

            return isValid
        }
    }
