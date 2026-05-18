package com.ssafy.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.auth.dto.PasswordResetVerifyResponseDto;
import com.ssafy.backend.domain.auth.exception.AuthErrorCode;
import com.ssafy.backend.domain.user.dto.UserAuthResponseDto;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.service.UserService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.security.Role;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  private static final Long USER_ID = 1L;
  private static final String EMAIL = "guardian@example.com";
  private static final String RAW_EMAIL = " Guardian@Example.com ";
  private static final String ENCODED_PASSWORD = "encoded-password";
  private static final String CODE = "123456";
  private static final String RESET_TOKEN = "reset-token";
  private static final String NEW_PASSWORD = "NewPassword1!";
  private static final long CODE_TTL_SECONDS = 300L;
  private static final long RESET_TOKEN_TTL_SECONDS = 600L;
  private static final long REQUEST_COOLDOWN_SECONDS = 60L;

  @Mock private UserService userService;
  @Mock private PasswordResetStore passwordResetStore;
  @Mock private PasswordResetCodeGenerator passwordResetCodeGenerator;
  @Mock private PasswordResetCodeSender passwordResetCodeSender;
  @Mock private UserTokenInvalidationService userTokenInvalidationService;

  private PasswordResetService passwordResetService;

  @BeforeEach
  void setUp() {
    PasswordResetProperties passwordResetProperties = new PasswordResetProperties();
    passwordResetProperties.setCodeLength(6);
    passwordResetProperties.setCodeTtlSeconds(CODE_TTL_SECONDS);
    passwordResetProperties.setResetTokenTtlSeconds(RESET_TOKEN_TTL_SECONDS);
    passwordResetProperties.setMaxVerifyAttempts(5);
    passwordResetProperties.setRequestCooldownSeconds(REQUEST_COOLDOWN_SECONDS);

    passwordResetService =
        new PasswordResetService(
            userService,
            passwordResetStore,
            passwordResetCodeGenerator,
            passwordResetCodeSender,
            passwordResetProperties,
            userTokenInvalidationService);
  }

  @Test
  @DisplayName("활성 비밀번호 계정이면 인증 코드를 저장하고 전달한다")
  void requestStoresAndSendsCodeForPasswordUser() {
    when(userService.getAuthInfoByEmail(EMAIL)).thenReturn(activePasswordUser());
    when(passwordResetCodeGenerator.generateCode(6)).thenReturn(CODE);

    passwordResetService.request(RAW_EMAIL);

    verify(passwordResetStore).saveCode(EMAIL, CODE, Duration.ofSeconds(CODE_TTL_SECONDS));
    verify(passwordResetStore)
        .saveRequestCooldown(EMAIL, Duration.ofSeconds(REQUEST_COOLDOWN_SECONDS));
    verify(passwordResetCodeSender).send(EMAIL, CODE, CODE_TTL_SECONDS);
  }

  @Test
  @DisplayName("재설정 요청 쿨다운 중이면 인증 코드를 다시 만들지 않는다")
  void requestSkipsWhenCooldownExists() {
    when(userService.getAuthInfoByEmail(EMAIL)).thenReturn(activePasswordUser());
    when(passwordResetStore.hasRequestCooldown(EMAIL)).thenReturn(true);

    passwordResetService.request(RAW_EMAIL);

    verify(passwordResetCodeGenerator, never()).generateCode(Mockito.anyInt());
    verify(passwordResetStore, never()).saveCode(Mockito.any(), Mockito.any(), Mockito.any());
    verify(passwordResetCodeSender, never()).send(Mockito.any(), Mockito.any(), Mockito.anyLong());
  }

  @Test
  @DisplayName("존재하지 않는 이메일이어도 성공처럼 처리하고 인증 코드를 만들지 않는다")
  void requestSkipsMissingEmailWithoutRevealingAccountExistence() {
    when(userService.getAuthInfoByEmail(EMAIL))
        .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

    passwordResetService.request(RAW_EMAIL);

    verify(passwordResetCodeGenerator, never()).generateCode(Mockito.anyInt());
    verify(passwordResetStore, never()).saveCode(Mockito.any(), Mockito.any(), Mockito.any());
    verify(passwordResetCodeSender, never()).send(Mockito.any(), Mockito.any(), Mockito.anyLong());
  }

  @Test
  @DisplayName("비활성 계정은 인증 코드를 만들지 않는다")
  void requestSkipsInactiveUser() {
    when(userService.getAuthInfoByEmail(EMAIL)).thenReturn(inactivePasswordUser());

    passwordResetService.request(RAW_EMAIL);

    verify(passwordResetCodeGenerator, never()).generateCode(Mockito.anyInt());
    verify(passwordResetStore, never()).saveCode(Mockito.any(), Mockito.any(), Mockito.any());
    verify(passwordResetCodeSender, never()).send(Mockito.any(), Mockito.any(), Mockito.anyLong());
  }

  @Test
  @DisplayName("소셜 전용 계정은 인증 코드를 만들지 않는다")
  void requestSkipsPasswordDisabledUser() {
    when(userService.getAuthInfoByEmail(EMAIL)).thenReturn(passwordDisabledUser());

    passwordResetService.request(RAW_EMAIL);

    verify(passwordResetCodeGenerator, never()).generateCode(Mockito.anyInt());
    verify(passwordResetStore, never()).saveCode(Mockito.any(), Mockito.any(), Mockito.any());
    verify(passwordResetCodeSender, never()).send(Mockito.any(), Mockito.any(), Mockito.anyLong());
  }

  @Test
  @DisplayName("인증 코드가 일치하면 코드를 삭제하고 재설정 토큰을 발급한다")
  void verifyDeletesCodeAndIssuesResetToken() {
    when(passwordResetStore.findCodeByEmail(EMAIL)).thenReturn(CODE);
    when(userService.getAuthInfoByEmail(EMAIL)).thenReturn(activePasswordUser());
    when(passwordResetCodeGenerator.generateResetToken()).thenReturn(RESET_TOKEN);

    PasswordResetVerifyResponseDto response = passwordResetService.verify(RAW_EMAIL, CODE);

    assertThat(response.resetToken()).isEqualTo(RESET_TOKEN);
    assertThat(response.expiresInSeconds()).isEqualTo(RESET_TOKEN_TTL_SECONDS);
    verify(passwordResetStore).deleteCode(EMAIL);
    verify(passwordResetStore)
        .saveResetToken(RESET_TOKEN, EMAIL, Duration.ofSeconds(RESET_TOKEN_TTL_SECONDS));
  }

  @Test
  @DisplayName("인증 코드가 없거나 일치하지 않으면 재설정 토큰을 발급하지 않는다")
  void verifyRejectsInvalidCode() {
    when(passwordResetStore.findCodeByEmail(EMAIL)).thenReturn("000000");

    assertAuthError(
        () -> passwordResetService.verify(RAW_EMAIL, CODE),
        AuthErrorCode.INVALID_PASSWORD_RESET_CODE);

    verify(passwordResetStore, never()).deleteCode(Mockito.any());
    verify(passwordResetStore).incrementVerifyAttempt(EMAIL, Duration.ofSeconds(CODE_TTL_SECONDS));
    verify(passwordResetCodeGenerator, never()).generateResetToken();
    verify(passwordResetStore, never()).saveResetToken(Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  @DisplayName("인증 코드 실패 횟수가 제한에 도달하면 인증 코드를 삭제한다")
  void verifyDeletesCodeWhenMaxAttemptsExceeded() {
    when(passwordResetStore.findCodeByEmail(EMAIL)).thenReturn("000000");
    when(passwordResetStore.incrementVerifyAttempt(EMAIL, Duration.ofSeconds(CODE_TTL_SECONDS)))
        .thenReturn(5L);

    assertAuthError(
        () -> passwordResetService.verify(RAW_EMAIL, CODE),
        AuthErrorCode.INVALID_PASSWORD_RESET_CODE);

    verify(passwordResetStore).deleteCode(EMAIL);
    verify(passwordResetCodeGenerator, never()).generateResetToken();
    verify(passwordResetStore, never()).saveResetToken(Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  @DisplayName("재설정 토큰이 유효하면 새 비밀번호를 저장하고 토큰을 무효화한다")
  void confirmResetsPasswordAndInvalidatesTokens() {
    when(passwordResetStore.findEmailByResetToken(RESET_TOKEN)).thenReturn(EMAIL);
    when(userService.resetPasswordByEmail(EMAIL, NEW_PASSWORD)).thenReturn(USER_ID);

    passwordResetService.confirm(RESET_TOKEN, NEW_PASSWORD);

    verify(userService).resetPasswordByEmail(EMAIL, NEW_PASSWORD);
    verify(passwordResetStore).deleteResetToken(RESET_TOKEN);
    verify(userTokenInvalidationService).invalidateAll(USER_ID);
  }

  @Test
  @DisplayName("재설정 토큰이 유효하지 않으면 비밀번호를 변경하지 않는다")
  void confirmRejectsInvalidResetToken() {
    when(passwordResetStore.findEmailByResetToken(RESET_TOKEN)).thenReturn(null);

    assertAuthError(
        () -> passwordResetService.confirm(RESET_TOKEN, NEW_PASSWORD),
        AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN);

    verify(userService, never()).resetPasswordByEmail(Mockito.any(), Mockito.any());
    verify(passwordResetStore, never()).deleteResetToken(Mockito.any());
    verify(userTokenInvalidationService, never()).invalidateAll(Mockito.any());
  }

  @Test
  @DisplayName("새 비밀번호 저장이 실패하면 재설정 토큰을 유지하고 토큰 무효화를 하지 않는다")
  void confirmKeepsResetTokenWhenPasswordResetFails() {
    when(passwordResetStore.findEmailByResetToken(RESET_TOKEN)).thenReturn(EMAIL);
    when(userService.resetPasswordByEmail(EMAIL, NEW_PASSWORD))
        .thenThrow(new BusinessException(UserErrorCode.NEW_PASSWORD_SAME_AS_CURRENT));

    assertUserError(
        () -> passwordResetService.confirm(RESET_TOKEN, NEW_PASSWORD),
        UserErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);

    verify(passwordResetStore, never()).deleteResetToken(Mockito.any());
    verify(userTokenInvalidationService, never()).invalidateAll(Mockito.any());
  }

  private UserAuthResponseDto activePasswordUser() {
    return new UserAuthResponseDto(USER_ID, EMAIL, ENCODED_PASSWORD, true, true, Role.USER);
  }

  private UserAuthResponseDto inactivePasswordUser() {
    return new UserAuthResponseDto(USER_ID, EMAIL, ENCODED_PASSWORD, true, false, Role.USER);
  }

  private UserAuthResponseDto passwordDisabledUser() {
    return new UserAuthResponseDto(USER_ID, EMAIL, ENCODED_PASSWORD, false, true, Role.USER);
  }

  private void assertAuthError(ThrowingCall call, AuthErrorCode expectedErrorCode) {
    assertThatThrownBy(call::invoke)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  private void assertUserError(ThrowingCall call, UserErrorCode expectedErrorCode) {
    assertThatThrownBy(call::invoke)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void invoke();
  }
}
