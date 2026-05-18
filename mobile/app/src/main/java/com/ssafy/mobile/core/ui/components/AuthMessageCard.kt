package com.ssafy.mobile.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AuthMessageCard(
    visible: Boolean,
    type: AuthMessageType,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = type.containerColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border =
            BorderStroke(
                width = 1.dp,
                color = type.contentColor().copy(alpha = 0.24f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AuthMessageBadge(type = type)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = type.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = type.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AuthMessageBadge(type: AuthMessageType) {
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(type.contentColor().copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = type.badgeText,
            style = MaterialTheme.typography.labelMedium,
            color = type.contentColor(),
            fontWeight = FontWeight.Bold,
        )
    }
}

enum class AuthMessageType {
    SignupComplete,
    DuplicateEmail,
    InvalidCredentials,
    InactiveAccount,
    InvalidProviderToken,
    SocialEmailAlreadyExists,
    ProviderError,
    Network,
    General,
    ;

    val title: String
        get() =
            when (this) {
                SignupComplete -> "회원가입이 완료되었습니다."
                DuplicateEmail -> "이미 사용 중인 이메일입니다."
                InvalidCredentials -> "이메일 또는 비밀번호를 확인해 주세요."
                InactiveAccount -> "로그인할 수 없는 계정입니다."
                InvalidProviderToken -> "소셜 로그인 인증에 실패했습니다."
                SocialEmailAlreadyExists -> "이미 가입된 이메일입니다."
                ProviderError -> "소셜 로그인 처리 중 문제가 발생했습니다."
                Network -> "네트워크 연결을 확인해 주세요."
                General -> "요청을 처리하지 못했습니다."
            }

    val description: String
        get() =
            when (this) {
                SignupComplete ->
                    "이제 로그인 화면으로 이동해 로그인해 주세요."
                DuplicateEmail ->
                    "다른 이메일을 입력하거나 로그인 화면으로 이동해 주세요."
                InvalidCredentials ->
                    "입력한 정보를 다시 확인한 뒤 시도해 주세요."
                InactiveAccount ->
                    "도움이 필요하면 관리자에게 문의해 주세요."
                InvalidProviderToken ->
                    "인증 화면을 다시 열어 로그인해 주세요."
                SocialEmailAlreadyExists ->
                    "기존 계정으로 로그인한 뒤 소셜 계정을 연결해 주세요."
                ProviderError ->
                    "잠시 후 다시 시도해 주세요."
                Network ->
                    "서버 연결 상태를 확인한 뒤 다시 시도해 주세요."
                General ->
                    "잠시 후 다시 시도해 주세요."
            }

    val badgeText: String
        get() =
            when (this) {
                SignupComplete -> "✓"
                else -> "!"
            }

    companion object {
        fun fromBackendCode(code: String?): AuthMessageType =
            when (code) {
                "AUTH_DUPLICATE_EMAIL" -> DuplicateEmail
                "AUTH_INVALID_CREDENTIALS" -> InvalidCredentials
                "AUTH_INACTIVE_ACCOUNT" -> InactiveAccount
                "OAUTH_INVALID_PROVIDER_TOKEN" -> InvalidProviderToken
                "OAUTH_EMAIL_ALREADY_EXISTS" -> SocialEmailAlreadyExists
                "OAUTH_PROVIDER_ERROR" -> ProviderError
                else -> General
            }
    }
}

@Composable
private fun AuthMessageType.containerColor() =
    when (this) {
        AuthMessageType.SignupComplete ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        else ->
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
    }

@Composable
private fun AuthMessageType.contentColor() =
    when (this) {
        AuthMessageType.SignupComplete -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
