package com.ssafy.mobile.feature.signup.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.R
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AuthMessageCard
import com.ssafy.mobile.core.ui.components.AuthMessageType
import com.ssafy.mobile.core.ui.components.AuthTextField
import com.ssafy.mobile.core.ui.theme.SudaFriendlyFontFamily

@Composable
fun SignupRoute(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val emailError by viewModel.emailError.collectAsStateWithLifecycle()
    val passwordError by viewModel.passwordError.collectAsStateWithLifecycle()
    val confirmPasswordError by viewModel.confirmPasswordError.collectAsStateWithLifecycle()
    val nameError by viewModel.nameError.collectAsStateWithLifecycle()

    SignupScreen(
        email = email,
        password = password,
        confirmPassword = confirmPassword,
        name = name,
        emailError = emailError,
        passwordError = passwordError,
        confirmPasswordError = confirmPasswordError,
        nameError = nameError,
        uiState = uiState,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onNameChanged = viewModel::onNameChanged,
        onSignupClick = viewModel::signup,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier,
    )
}

@Composable
@Suppress("LongParameterList")
private fun SignupScreen(
    email: String,
    password: String,
    confirmPassword: String,
    name: String,
    emailError: String?,
    passwordError: String?,
    confirmPasswordError: String?,
    nameError: String?,
    uiState: SignupUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onSignupClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState is SignupUiState.Loading

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 32.dp)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SignupHeader()

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
            ) {
                val messageType =
                    when (uiState) {
                        is SignupUiState.Error -> uiState.type
                        SignupUiState.Success -> AuthMessageType.SignupComplete
                        else -> AuthMessageType.General
                    }
                val isMessageVisible =
                    uiState is SignupUiState.Error || uiState is SignupUiState.Success

                AuthMessageCard(
                    visible = isMessageVisible,
                    type = messageType,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isMessageVisible) {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                SignupInputFields(
                    email = email,
                    password = password,
                    confirmPassword = confirmPassword,
                    name = name,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError,
                    nameError = nameError,
                    isLoading = isLoading,
                    onEmailChanged = onEmailChanged,
                    onPasswordChanged = onPasswordChanged,
                    onConfirmPasswordChanged = onConfirmPasswordChanged,
                    onNameChanged = onNameChanged,
                    onSignupClick = onSignupClick,
                )

                AppPrimaryButton(
                    text = "회원가입",
                    onClick = onSignupClick,
                    enabled = !isLoading,
                    loading = isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            LoginTextLink(onNavigateToLogin = onNavigateToLogin)
        }
    }
}

@Composable
private fun SignupHeader() {
    Text(
        text = "보호자 계정을 만들어 주세요",
        style =
            MaterialTheme.typography.titleMedium.copy(
                fontFamily = SudaFriendlyFontFamily,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun SignupInputFields(
    email: String,
    password: String,
    confirmPassword: String,
    name: String,
    emailError: String?,
    passwordError: String?,
    confirmPasswordError: String?,
    nameError: String?,
    isLoading: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onSignupClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var passwordsVisible by rememberSaveable { mutableStateOf(false) }
    val passwordTransformation =
        if (passwordsVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        }

    AuthTextField(
        value = email,
        onValueChange = onEmailChanged,
        label = "이메일",
        isError = emailError != null,
        supportingText = emailError,
        enabled = !isLoading,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        keyboardActions =
            KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
        modifier = Modifier.fillMaxWidth(),
    )

    AuthTextField(
        value = name,
        onValueChange = onNameChanged,
        label = "보호자 이름",
        isError = nameError != null,
        supportingText = nameError,
        enabled = !isLoading,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        keyboardActions =
            KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
        modifier = Modifier.fillMaxWidth(),
    )

    AuthTextField(
        value = password,
        onValueChange = onPasswordChanged,
        label = "비밀번호",
        isError = passwordError != null,
        supportingText = passwordError,
        enabled = !isLoading,
        visualTransformation = passwordTransformation,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
        keyboardActions =
            KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
        trailingIcon = {
            PasswordVisibilityButton(
                visible = passwordsVisible,
                onToggle = { passwordsVisible = !passwordsVisible },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    AuthTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChanged,
        label = "비밀번호 확인",
        isError = confirmPasswordError != null,
        supportingText = confirmPasswordError,
        enabled = !isLoading,
        visualTransformation = passwordTransformation,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onSignupClick()
                },
            ),
        trailingIcon = {
            PasswordVisibilityButton(
                visible = passwordsVisible,
                onToggle = { passwordsVisible = !passwordsVisible },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordVisibilityButton(
    visible: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        Icon(
            painter =
                painterResource(
                    id = if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                ),
            contentDescription = if (visible) "비밀번호 숨기기" else "비밀번호 보기",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoginTextLink(onNavigateToLogin: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "이미 계정이 있나요?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onNavigateToLogin) {
            Text(
                text = "로그인",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
