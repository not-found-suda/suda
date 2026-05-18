package com.ssafy.backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.auth.service.UserTokenInvalidationService;
import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.domain.user.exception.UserErrorCode;
import com.ssafy.backend.domain.user.repository.UserRepository;
import com.ssafy.backend.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceWithdrawTest {

  private static final Long USER_ID = 1L;
  private static final String PASSWORD = "Password1!";
  private static final String ENCODED_PASSWORD = "encoded-password";

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserTokenInvalidationService userTokenInvalidationService;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, passwordEncoder, userTokenInvalidationService);
  }

  @Test
  @DisplayName("비밀번호 로그인 계정은 현재 비밀번호가 일치하면 탈퇴 처리하고 토큰을 무효화한다")
  void withdrawPasswordUser() {
    User user = passwordUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

    userService.withdraw(USER_ID, PASSWORD);

    assertThat(user.isActive()).isFalse();
    verify(userRepository).flush();
    verify(userTokenInvalidationService).invalidateAll(USER_ID);
  }

  @Test
  @DisplayName("비밀번호 로그인 계정은 탈퇴 시 현재 비밀번호가 필요하다")
  void withdrawPasswordUserRequiresPassword() {
    User user = passwordUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    assertUserError(
        () -> userService.withdraw(USER_ID, null), UserErrorCode.WITHDRAW_PASSWORD_REQUIRED);

    assertThat(user.isActive()).isTrue();
    verify(passwordEncoder, never()).matches(Mockito.any(), Mockito.any());
    verify(userRepository, never()).flush();
    verify(userTokenInvalidationService, never()).invalidateAll(Mockito.any());
  }

  @Test
  @DisplayName("비밀번호 로그인 계정은 현재 비밀번호가 틀리면 탈퇴 처리하지 않는다")
  void withdrawPasswordUserRejectsPasswordMismatch() {
    User user = passwordUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

    assertUserError(
        () -> userService.withdraw(USER_ID, PASSWORD), UserErrorCode.CURRENT_PASSWORD_MISMATCH);

    assertThat(user.isActive()).isTrue();
    verify(userRepository, never()).flush();
    verify(userTokenInvalidationService, never()).invalidateAll(Mockito.any());
  }

  @Test
  @DisplayName("소셜 전용 계정은 비밀번호 없이 탈퇴 처리하고 토큰을 무효화한다")
  void withdrawOAuthOnlyUserWithoutPassword() {
    User user = oauthOnlyUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    userService.withdraw(USER_ID, null);

    assertThat(user.isActive()).isFalse();
    verify(passwordEncoder, never()).matches(Mockito.any(), Mockito.any());
    verify(userRepository).flush();
    verify(userTokenInvalidationService).invalidateAll(USER_ID);
  }

  @Test
  @DisplayName("이미 탈퇴한 계정은 다시 탈퇴 처리하지 않는다")
  void withdrawRejectsInactiveUser() {
    User user = passwordUser();
    user.withdraw();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    assertUserError(() -> userService.withdraw(USER_ID, PASSWORD), UserErrorCode.USER_NOT_FOUND);

    verify(passwordEncoder, never()).matches(Mockito.any(), Mockito.any());
    verify(userRepository, never()).flush();
    verify(userTokenInvalidationService, never()).invalidateAll(Mockito.any());
  }

  @Test
  @DisplayName("사용자를 찾을 수 없으면 탈퇴 처리하지 않는다")
  void withdrawRejectsMissingUser() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertUserError(() -> userService.withdraw(USER_ID, PASSWORD), UserErrorCode.USER_NOT_FOUND);

    verify(passwordEncoder, never()).matches(Mockito.any(), Mockito.any());
    verify(userRepository, never()).flush();
    verify(userTokenInvalidationService, never()).invalidateAll(Mockito.any());
  }

  private User passwordUser() {
    return User.create("guardian@example.com", ENCODED_PASSWORD, "보호자");
  }

  private User oauthOnlyUser() {
    return User.createOAuthUser("guardian@example.com", ENCODED_PASSWORD, "보호자");
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
