package com.ssafy.mobile.feature.login.presentation

import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.auth.TokenStorage
import com.ssafy.mobile.core.session.ActiveChildStorage
import com.ssafy.mobile.feature.learning.data.repository.LearningQuizAnswerSubmissionQueueSyncer
import com.ssafy.mobile.feature.login.data.dto.LoginResponseDto
import com.ssafy.mobile.feature.login.data.oauth.NaverOAuthManager
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
        private val naverOAuthManager: NaverOAuthManager,
        private val learningQuizAnswerSubmissionQueueSyncer:
            LearningQuizAnswerSubmissionQueueSyncer,
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

        fun initializeNaverSdk(context: Context) {
            naverOAuthManager.initialize(context)
        }

        fun isNaverConfigValid(): Boolean = naverOAuthManager.isConfigValid

        fun login() {
            if (_uiState.value is LoginUiState.Loading) return
            if (!validateInputs()) return

            viewModelScope.launch {
                _uiState.value = LoginUiState.Loading
                try {
                    val response =
                        withContext(Dispatchers.IO) {
                            loginRepository.login(_email.value.trim(), _password.value)
                        }

                    handleLoginSuccess(response)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LoginException) {
                    showLoginError(e.message ?: "로그인에 실패했습니다.")
                } catch (e: IOException) {
                    Log.e(TAG, "Login network error", e)
                    showLoginError("네트워크 연결을 확인해 주세요.")
                } catch (e: IllegalStateException) {
                    showLoginError(e.message ?: "알 수 없는 오류가 발생했습니다.")
                }
            }
        }

        fun loginWithNaverToken(providerAccessToken: String) {
            if (_uiState.value is LoginUiState.Loading) return
            if (providerAccessToken.isBlank()) {
                showLoginError("네이버 인증 토큰이 유효하지 않습니다.")
                return
            }

            viewModelScope.launch {
                _uiState.value = LoginUiState.Loading
                try {
                    val response =
                        withContext(Dispatchers.IO) {
                            loginRepository.loginWithNaver(providerAccessToken)
                        }

                    handleLoginSuccess(response)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LoginException) {
                    showLoginError(e.message ?: "네이버 로그인에 실패했습니다.")
                } catch (e: IOException) {
                    Log.e(TAG, "Naver login network error", e)
                    showLoginError("네트워크 연결을 확인해 주세요.")
                } catch (e: IllegalStateException) {
                    showLoginError(e.message ?: "네이버 로그인 중 오류가 발생했습니다.")
                }
            }
        }

        fun onNaverLoginError(message: String) {
            showLoginError(message)
        }

        private suspend fun handleLoginSuccess(response: LoginResponseDto) {
            try {
                withContext(Dispatchers.IO) {
                    tokenStorage.clearTokens()
                    tokenStorage.saveTokens(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Token storage failed", e)
                showLoginError("로그인 정보를 저장하지 못했습니다. 다시 시도해 주세요.")
                return
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Token storage failed", e)
                showLoginError("로그인 정보를 저장하지 못했습니다. 다시 시도해 주세요.")
                return
            }

            val hasActiveChild =
                withContext(Dispatchers.IO) {
                    activeChildStorage.getActiveChildId() != null
                }

            learningQuizAnswerSubmissionQueueSyncer.requestSync()
            _uiState.value = LoginUiState.Success(hasActiveChild = hasActiveChild)
        }

        private fun showLoginError(message: String) {
            _uiState.value = LoginUiState.Error(message = message)
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
