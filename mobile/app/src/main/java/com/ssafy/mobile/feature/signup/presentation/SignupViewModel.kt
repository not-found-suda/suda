package com.ssafy.mobile.feature.signup.presentation

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.signup.data.repository.SignupException
import com.ssafy.mobile.feature.signup.data.repository.SignupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SignupViewModel
    @Inject
    constructor(
        private val signupRepository: SignupRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "SignupViewModel"
            private const val PASSWORD_MIN_LENGTH = 8
            private const val PASSWORD_MAX_LENGTH = 20
            private const val PWD_LETTERS = "(?=.*[A-Za-z])"
            private const val PWD_DIGITS = "(?=.*\\d)"
            private const val PWD_SPECIALS = "(?=.*[!@#\$%^&*()_+\\-={}\\[\\]:;,.?/~])"
            private const val PWD_ALLOWED_CHARS = "[A-Za-z\\d!@#\$%^&*()_+\\-={}\\[\\]:;,.?/~]"
            private val PASSWORD_PATTERN =
                Regex(
                    "^$PWD_LETTERS$PWD_DIGITS$PWD_SPECIALS" +
                        "$PWD_ALLOWED_CHARS{$PASSWORD_MIN_LENGTH,$PASSWORD_MAX_LENGTH}\$",
                )
        }

        private val _uiState = MutableStateFlow<SignupUiState>(SignupUiState.Idle)
        val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

        private val _email = MutableStateFlow("")
        val email: StateFlow<String> = _email.asStateFlow()

        private val _password = MutableStateFlow("")
        val password: StateFlow<String> = _password.asStateFlow()

        private val _confirmPassword = MutableStateFlow("")
        val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

        private val _emailError = MutableStateFlow<String?>(null)
        val emailError: StateFlow<String?> = _emailError.asStateFlow()

        private val _passwordError = MutableStateFlow<String?>(null)
        val passwordError: StateFlow<String?> = _passwordError.asStateFlow()

        private val _confirmPasswordError = MutableStateFlow<String?>(null)
        val confirmPasswordError: StateFlow<String?> = _confirmPasswordError.asStateFlow()

        fun onEmailChanged(value: String) {
            _email.value = value
            _emailError.value = null
        }

        fun onPasswordChanged(value: String) {
            _password.value = value
            _passwordError.value = null
        }

        fun onConfirmPasswordChanged(value: String) {
            _confirmPassword.value = value
            _confirmPasswordError.value = null
        }

        fun signup() {
            if (!validateInputs()) return

            _uiState.value = SignupUiState.Loading

            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        signupRepository.signup(_email.value.trim(), _password.value)
                    }

                    _uiState.value = SignupUiState.Success
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SignupException) {
                    _uiState.value =
                        SignupUiState.Error(
                            message = e.message ?: "회원가입에 실패했습니다.",
                        )
                } catch (e: IOException) {
                    Log.e(TAG, "Signup network error", e)
                    _uiState.value =
                        SignupUiState.Error(
                            message = "네트워크 연결을 확인해 주세요.",
                        )
                } catch (e: IllegalStateException) {
                    _uiState.value =
                        SignupUiState.Error(
                            message = e.message ?: "알 수 없는 오류가 발생했습니다.",
                        )
                }
            }
        }

        fun clearError() {
            if (_uiState.value is SignupUiState.Error) {
                _uiState.value = SignupUiState.Idle
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

            val passwordValue = _password.value
            if (passwordValue.isEmpty()) {
                _passwordError.value = "비밀번호를 입력해 주세요."
                isValid = false
            } else if (!PASSWORD_PATTERN.matches(passwordValue)) {
                _passwordError.value = "영문, 숫자, 특수문자를 포함해 8~20자로 입력해 주세요."
                isValid = false
            }

            val confirmPasswordValue = _confirmPassword.value
            if (confirmPasswordValue.isEmpty()) {
                _confirmPasswordError.value = "비밀번호 확인을 입력해 주세요."
                isValid = false
            } else if (passwordValue != confirmPasswordValue) {
                _confirmPasswordError.value = "비밀번호가 일치하지 않습니다."
                isValid = false
            }

            return isValid
        }
    }
