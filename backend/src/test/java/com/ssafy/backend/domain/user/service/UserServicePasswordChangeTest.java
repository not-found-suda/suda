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
class UserServicePasswordChangeTest {

  private static final Long USER_ID = 1L;
  private static final String CURRENT_PASSWORD = "Password1!";
  private static final String NEW_PASSWORD = "NewPassword1!";
  private static final String ENCODED_CURRENT_PASSWORD = "encoded-current-password";
  private static final String ENCODED_NEW_PASSWORD = "encoded-new-password";

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserTokenInvalidationService userTokenInvalidationService;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, passwordEncoder, userTokenInvalidationService);
  }

  @Test
  @DisplayName("비밀번호 로그인을 사용할 수 없는 계정은 비밀번호를 변경하지 않는다")
  void changePasswordRejectsPasswordDisabledUser() {
    User user = oauthOnlyUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    assertUserError(
        () -> userService.changePassword(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD),
        UserErrorCode.PASSWORD_LOGIN_NOT_ENABLED);

    verify(passwordEncoder, never()).matches(Mockito.any(), Mockito.any());
    verify(passwordEncoder, never()).encode(Mockito.any());
    verify(userRepository, never()).flush();
  }

  @Test
  @DisplayName("현재 비밀번호가 일치하지 않으면 비밀번호를 변경하지 않는다")
  void changePasswordRejectsCurrentPasswordMismatch() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_CURRENT_PASSWORD)).thenReturn(false);

    assertUserError(
        () -> userService.changePassword(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD),
        UserErrorCode.CURRENT_PASSWORD_MISMATCH);

    assertThat(user.getPassword()).isEqualTo(ENCODED_CURRENT_PASSWORD);
    verify(passwordEncoder, never()).encode(Mockito.any());
    verify(userRepository, never()).flush();
  }

  @Test
  @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 비밀번호를 변경하지 않는다")
  void changePasswordRejectsSameAsCurrentPassword() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_CURRENT_PASSWORD)).thenReturn(true);
    when(passwordEncoder.matches(NEW_PASSWORD, ENCODED_CURRENT_PASSWORD)).thenReturn(true);

    assertUserError(
        () -> userService.changePassword(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD),
        UserErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);

    assertThat(user.getPassword()).isEqualTo(ENCODED_CURRENT_PASSWORD);
    verify(passwordEncoder, never()).encode(Mockito.any());
    verify(userRepository, never()).flush();
  }

  @Test
  @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 다르면 인코딩 후 저장한다")
  void changePasswordEncodesAndStoresNewPassword() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_CURRENT_PASSWORD)).thenReturn(true);
    when(passwordEncoder.matches(NEW_PASSWORD, ENCODED_CURRENT_PASSWORD)).thenReturn(false);
    when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_NEW_PASSWORD);

    userService.changePassword(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD);

    assertThat(user.getPassword()).isEqualTo(ENCODED_NEW_PASSWORD);
    verify(userRepository).flush();
  }

  @Test
  @DisplayName("사용자를 찾을 수 없으면 비밀번호를 변경하지 않는다")
  void changePasswordRejectsMissingUser() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertUserError(
        () -> userService.changePassword(USER_ID, CURRENT_PASSWORD, NEW_PASSWORD),
        UserErrorCode.USER_NOT_FOUND);

    verify(passwordEncoder, never()).matches(Mockito.any(), Mockito.any());
    verify(passwordEncoder, never()).encode(Mockito.any());
    verify(userRepository, never()).flush();
  }

  private User user() {
    return User.create("guardian@example.com", ENCODED_CURRENT_PASSWORD, "보호자");
  }

  private User oauthOnlyUser() {
    return User.createOAuthUser("guardian@example.com", ENCODED_CURRENT_PASSWORD, "보호자");
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
