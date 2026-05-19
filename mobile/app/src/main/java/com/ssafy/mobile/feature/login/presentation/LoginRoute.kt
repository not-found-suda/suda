@file:Suppress("FunctionNaming")

package com.ssafy.mobile.feature.login.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navercorp.nid.NaverIdLoginSDK
import com.ssafy.mobile.R
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.AuthMessageCard
import com.ssafy.mobile.core.ui.components.AuthMessageType
import com.ssafy.mobile.core.ui.components.AuthTextField
import com.ssafy.mobile.core.ui.theme.SudaFriendlyFontFamily

@Composable
fun LoginRoute(
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onNavigateToConversation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val emailError by viewModel.emailError.collectAsStateWithLifecycle()
    val passwordError by viewModel.passwordError.collectAsStateWithLifecycle()
    val naverLoginLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val token = NaverIdLoginSDK.getAccessToken()
            if (token.isNullOrBlank()) {
                val errorCode = NaverIdLoginSDK.getLastErrorCode().code
                if (errorCode != NAVER_USER_CANCEL_CODE) {
                    val errorDescription = NaverIdLoginSDK.getLastErrorDescription().orEmpty()
                    viewModel.onNaverLoginError(
                        errorDescription.ifBlank { "네이버 로그인에 실패했습니다." },
                    )
                }
            } else {
                viewModel.loginWithNaverToken(token)
            }
        }

    LaunchedEffect(Unit) {
        viewModel.initializeNaverSdk(context)
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginUiState.Success -> {
                onNavigateToHome()
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
        isNaverLoginEnabled = viewModel.isNaverConfigValid(),
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onLoginClick = viewModel::login,
        onSignupClick = onNavigateToSignup,
        onConversationClick = onNavigateToConversation,
        onNaverLoginClick = {
            if (viewModel.isNaverConfigValid()) {
                NaverIdLoginSDK.authenticate(context, naverLoginLauncher)
            } else {
                viewModel.onNaverLoginError("네이버 로그인 설정을 확인해 주세요.")
            }
        },
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
    isNaverLoginEnabled: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    onSignupClick: () -> Unit,
    onConversationClick: () -> Unit,
    onNaverLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = uiState is LoginUiState.Loading

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 40.dp)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoginHeader()

            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
            ) {
                val errorState = uiState as? LoginUiState.Error
                AuthMessageCard(
                    visible = errorState != null,
                    type = errorState?.type ?: AuthMessageType.General,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorState != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                }

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

                AppPrimaryButton(
                    text = "로그인",
                    onClick = onLoginClick,
                    enabled = !isLoading,
                    loading = isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                AuthDivider(modifier = Modifier.padding(vertical = 18.dp))

                NaverLoginButton(
                    onClick = onNaverLoginClick,
                    enabled = !isLoading && isNaverLoginEnabled,
                )

                Spacer(modifier = Modifier.height(12.dp))

                AppSecondaryButton(
                    text = "\uAC8C\uC2A4\uD2B8 \uBAA8\uB4DC",
                    onClick = onConversationClick,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SignupTextLink(onSignupClick = onSignupClick)
        }
    }
}

@Composable
private fun LoginHeader() {
    Image(
        painter = painterResource(id = R.drawable.suda_wordmark),
        contentDescription = "SUDA",
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        modifier =
            Modifier
                .fillMaxWidth(LOGO_WIDTH_FRACTION)
                .height(LOGO_HEIGHT_DP.dp),
    )
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = "보호자 계정으로 로그인해 주세요",
        style =
            MaterialTheme.typography.titleMedium.copy(
                fontFamily = SudaFriendlyFontFamily,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
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
        value = password,
        onValueChange = onPasswordChanged,
        label = "비밀번호",
        isError = passwordError != null,
        supportingText = passwordError,
        enabled = !isLoading,
        visualTransformation = PasswordVisualTransformation(),
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
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = "또는",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun NaverLoginButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = naverGreen,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Text(
            text = "네이버로 로그인",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SignupTextLink(onSignupClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "아직 계정이 없나요?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(2.dp))
        TextButton(onClick = onSignupClick) {
            Text(
                text = "회원가입",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val NAVER_USER_CANCEL_CODE = "user_cancel"
private const val LOGO_WIDTH_FRACTION = 0.9f
private const val LOGO_HEIGHT_DP = 120

@Suppress("MagicNumber")
private val naverGreen = Color(0xFF03C75A)
