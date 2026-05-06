package com.ssafy.mobile.feature.login.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginRoute(
    onNavigateToHome: () -> Unit,
    onNavigateToChildSelect: () -> Unit,
    onNavigateToSignup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val emailError by viewModel.emailError.collectAsStateWithLifecycle()
    val passwordError by viewModel.passwordError.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LoginUiState.Success -> {
                if (state.hasActiveChild) {
                    onNavigateToHome()
                } else {
                    onNavigateToChildSelect()
                }
            }
            else -> Unit
        }
    }

    LoginScreen(
        email = email,
        password = password,
        emailError = emailError,
        passwordError = passwordError,
        uiState = uiState,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onLoginClick = viewModel::login,
        onSignupClick = onNavigateToSignup,
        modifier = modifier,
    )
}

@Composable
@Suppress("LongParameterList")
private fun LoginScreen(
    email: String,
    password: String,
    emailError: String?,
    passwordError: String?,
    uiState: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    onSignupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState is LoginUiState.Loading

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoginHeader()

        Spacer(modifier = Modifier.height(32.dp))

        LoginInputFields(
            email = email,
            password = password,
            emailError = emailError,
            passwordError = passwordError,
            isLoading = isLoading,
            onEmailChanged = onEmailChanged,
            onPasswordChanged = onPasswordChanged,
            onLoginClick = onLoginClick,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState is LoginUiState.Error) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        LoginActionButtons(
            isLoading = isLoading,
            onLoginClick = onLoginClick,
            onSignupClick = onSignupClick,
        )
    }
}

@Composable
private fun LoginHeader() {
    Text(
        text = "SUDA",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "보호자 로그인",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
@Suppress("LongParameterList")
private fun LoginInputFields(
    email: String,
    password: String,
    emailError: String?,
    passwordError: String?,
    isLoading: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChanged,
        label = { Text("이메일") },
        placeholder = { Text("example@email.com") },
        isError = emailError != null,
        supportingText =
            emailError?.let {
                { Text(text = it, color = MaterialTheme.colorScheme.error) }
            },
        singleLine = true,
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

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChanged,
        label = { Text("비밀번호") },
        isError = passwordError != null,
        supportingText =
            passwordError?.let {
                { Text(text = it, color = MaterialTheme.colorScheme.error) }
            },
        singleLine = true,
        enabled = !isLoading,
        visualTransformation =
            if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onLoginClick()
                },
            ),
        trailingIcon = {
            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                Text(if (passwordVisible) "숨기기" else "보기")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LoginActionButtons(
    isLoading: Boolean,
    onLoginClick: () -> Unit,
    onSignupClick: () -> Unit,
) {
    Button(
        onClick = onLoginClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.height(20.dp),
            )
        } else {
            Text(text = "로그인")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onSignupClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "회원가입")
    }

    Spacer(modifier = Modifier.height(24.dp))

    // [S14P31A404-232] 네이버 로그인 SDK 연동은 후속 티켓에서 구현
    OutlinedButton(
        onClick = { },
        enabled = false,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "네이버로 로그인 (준비 중)")
    }
}
