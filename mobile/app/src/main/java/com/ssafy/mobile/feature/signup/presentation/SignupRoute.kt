package com.ssafy.mobile.feature.signup.presentation

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
fun SignupRoute(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
    val emailError by viewModel.emailError.collectAsStateWithLifecycle()
    val passwordError by viewModel.passwordError.collectAsStateWithLifecycle()
    val confirmPasswordError by viewModel.confirmPasswordError.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is SignupUiState.Success) {
            onNavigateToLogin()
        }
    }

    SignupScreen(
        email = email,
        password = password,
        confirmPassword = confirmPassword,
        emailError = emailError,
        passwordError = passwordError,
        confirmPasswordError = confirmPasswordError,
        uiState = uiState,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
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
    emailError: String?,
    passwordError: String?,
    confirmPasswordError: String?,
    uiState: SignupUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSignupClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState is SignupUiState.Loading

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
        SignupHeader()

        Spacer(modifier = Modifier.height(32.dp))

        SignupInputFields(
            email = email,
            password = password,
            confirmPassword = confirmPassword,
            emailError = emailError,
            passwordError = passwordError,
            confirmPasswordError = confirmPasswordError,
            isLoading = isLoading,
            onEmailChanged = onEmailChanged,
            onPasswordChanged = onPasswordChanged,
            onConfirmPasswordChanged = onConfirmPasswordChanged,
            onSignupClick = onSignupClick,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState is SignupUiState.Error) {
            Text(
                text = uiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        SignupActionButtons(
            isLoading = isLoading,
            onSignupClick = onSignupClick,
            onNavigateToLogin = onNavigateToLogin,
        )
    }
}

@Composable
private fun SignupHeader() {
    Text(
        text = "SUDA",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "보호자 회원가입",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun SignupInputFields(
    email: String,
    password: String,
    confirmPassword: String,
    emailError: String?,
    passwordError: String?,
    confirmPasswordError: String?,
    isLoading: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSignupClick: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

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
                imeAction = ImeAction.Next,
            ),
        keyboardActions =
            KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
        trailingIcon = {
            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                Text(if (passwordVisible) "숨기기" else "보기")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChanged,
        label = { Text("비밀번호 확인") },
        isError = confirmPasswordError != null,
        supportingText =
            confirmPasswordError?.let {
                { Text(text = it, color = MaterialTheme.colorScheme.error) }
            },
        singleLine = true,
        enabled = !isLoading,
        visualTransformation =
            if (confirmPasswordVisible) {
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
                    onSignupClick()
                },
            ),
        trailingIcon = {
            TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                Text(if (confirmPasswordVisible) "숨기기" else "보기")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SignupActionButtons(
    isLoading: Boolean,
    onSignupClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    Button(
        onClick = onSignupClick,
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
            Text(text = "회원가입")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onNavigateToLogin,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "로그인으로 돌아가기")
    }
}
