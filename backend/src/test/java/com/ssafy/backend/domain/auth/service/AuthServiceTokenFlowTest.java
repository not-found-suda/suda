package com.ssafy.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.backend.domain.auth.dto.LoginRequestDto;
import com.ssafy.backend.domain.auth.dto.LoginResponseDto;
import com.ssafy.backend.domain.auth.dto.RefreshTokenResponseDto;
import com.ssafy.backend.domain.auth.exception.AuthErrorCode;
import com.ssafy.backend.domain.user.dto.UserAuthResponseDto;
import com.ssafy.backend.domain.user.service.UserService;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.security.Role;
import com.ssafy.backend.global.security.jwt.JwtProperties;
import com.ssafy.backend.global.security.jwt.JwtTokenProvider;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTokenFlowTest {

  private static final Long USER_ID = 1L;
  private static final String EMAIL = "guardian@example.com";
  private static final String RAW_PASSWORD = "password1234";
  private static final String ENCODED_PASSWORD = "encoded-password";
  private static final String ACCESS_TOKEN = "access-token";
  private static final String REFRESH_TOKEN = "refresh-token";
  private static final String ACCESS_JTI = "access-jti";
  private static final String REFRESH_JTI = "refresh-jti";
  private static final long ACCESS_TOKEN_TTL_SECONDS = 900L;
  private static final long REFRESH_TOKEN_TTL_SECONDS = 1_209_600L;

  @Mock private UserService userService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private RefreshTokenStore refreshTokenStore;
  @Mock private AccessTokenBlacklistStore accessTokenBlacklistStore;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setAccessTokenTtlSeconds(ACCESS_TOKEN_TTL_SECONDS);
    jwtProperties.setRefreshTokenTtlSeconds(REFRESH_TOKEN_TTL_SECONDS);

    authService =
        new AuthService(
            userService,
            passwordEncoder,
            jwtTokenProvider,
            jwtProperties,
            refreshTokenStore,
            accessTokenBlacklistStore);
  }

  @Test
  @DisplayName("로그인 성공 시 accessToken과 refreshToken을 발급하고 refreshToken을 저장한다")
  void loginIssuesTokenPairAndStoresRefreshToken() {
    when(userService.getAuthInfoByEmail(EMAIL)).thenReturn(activeUser());
    when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
    stubTokenCreation(ACCESS_TOKEN, REFRESH_TOKEN, REFRESH_JTI);

    AuthService.LoginResult result = authService.login(new LoginRequestDto(EMAIL, RAW_PASSWORD));

    LoginResponseDto response = result.response();
    assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
    assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_TTL_SECONDS);
    assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
    verify(refreshTokenStore)
        .save(REFRESH_JTI, USER_ID, Duration.ofSeconds(REFRESH_TOKEN_TTL_SECONDS));
  }

  @Test
  @DisplayName("비밀번호가 일치하지 않으면 로그인 토큰을 발급하지 않는다")
  void loginRejectsInvalidPassword() {
    when(userService.getAuthInfoByEmail(EMAIL)).thenReturn(activeUser());
    when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

    assertAuthError(
        () -> authService.login(new LoginRequestDto(EMAIL, RAW_PASSWORD)),
        AuthErrorCode.INVALID_CREDENTIALS);
    verifyTokenPairNotGenerated();
    verify(refreshTokenStore, never()).save(Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  @DisplayName("유효한 refreshToken이면 기존 토큰을 삭제하고 새 토큰 쌍을 발급한다")
  void refreshRotatesRefreshToken() {
    String oldRefreshToken = "old-refresh-token";
    String oldRefreshJti = "old-refresh-jti";
    String newAccessToken = "new-access-token";
    String newRefreshToken = "new-refresh-token";
    String newRefreshJti = "new-refresh-jti";

    when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);
    when(jwtTokenProvider.isRefreshToken(oldRefreshToken)).thenReturn(true);
    when(jwtTokenProvider.getJti(oldRefreshToken)).thenReturn(oldRefreshJti);
    when(jwtTokenProvider.getUserId(oldRefreshToken)).thenReturn(USER_ID);
    when(refreshTokenStore.findUserIdByJti(oldRefreshJti)).thenReturn(USER_ID);
    when(userService.getAuthInfoById(USER_ID)).thenReturn(activeUser());
    stubTokenCreation(newAccessToken, newRefreshToken, newRefreshJti);

    AuthService.RefreshTokenResult result = authService.refresh(oldRefreshToken);

    RefreshTokenResponseDto response = result.response();
    assertThat(response.accessToken()).isEqualTo(newAccessToken);
    assertThat(response.refreshToken()).isEqualTo(newRefreshToken);
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_TTL_SECONDS);

    InOrder inOrder = Mockito.inOrder(refreshTokenStore);
    inOrder.verify(refreshTokenStore).delete(oldRefreshJti);
    inOrder
        .verify(refreshTokenStore)
        .save(newRefreshJti, USER_ID, Duration.ofSeconds(REFRESH_TOKEN_TTL_SECONDS));
  }

  @Test
  @DisplayName("저장소에서 찾을 수 없는 refreshToken이면 재발급을 거부한다")
  void refreshRejectsTokenMissingFromStore() {
    String oldRefreshToken = "old-refresh-token";
    String oldRefreshJti = "old-refresh-jti";

    when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);
    when(jwtTokenProvider.isRefreshToken(oldRefreshToken)).thenReturn(true);
    when(jwtTokenProvider.getJti(oldRefreshToken)).thenReturn(oldRefreshJti);
    when(jwtTokenProvider.getUserId(oldRefreshToken)).thenReturn(USER_ID);
    when(refreshTokenStore.findUserIdByJti(oldRefreshJti)).thenReturn(null);

    assertAuthError(
        () -> authService.refresh(oldRefreshToken), AuthErrorCode.INVALID_REFRESH_TOKEN);
    verify(refreshTokenStore, never()).delete(oldRefreshJti);
    verify(refreshTokenStore, never()).save(Mockito.any(), Mockito.any(), Mockito.any());
    verifyTokenPairNotGenerated();
  }

  @Test
  @DisplayName("로그아웃 시 refreshToken은 삭제하고 accessToken은 블랙리스트에 등록한다")
  void logoutDeletesRefreshTokenAndBlacklistsAccessToken() {
    when(jwtTokenProvider.validateToken(REFRESH_TOKEN)).thenReturn(true);
    when(jwtTokenProvider.isRefreshToken(REFRESH_TOKEN)).thenReturn(true);
    when(jwtTokenProvider.getJti(REFRESH_TOKEN)).thenReturn(REFRESH_JTI);
    when(jwtTokenProvider.validateToken(ACCESS_TOKEN)).thenReturn(true);
    when(jwtTokenProvider.isAccessToken(ACCESS_TOKEN)).thenReturn(true);
    when(jwtTokenProvider.getJti(ACCESS_TOKEN)).thenReturn(ACCESS_JTI);
    when(jwtTokenProvider.getRemainingValiditySeconds(ACCESS_TOKEN)).thenReturn(300L);

    authService.logout(REFRESH_TOKEN, ACCESS_TOKEN);

    verify(refreshTokenStore).delete(REFRESH_JTI);
    verify(accessTokenBlacklistStore).blacklist(ACCESS_JTI, Duration.ofSeconds(300L));
  }

  @Test
  @DisplayName("로그아웃 시 accessToken 남은 시간이 없으면 블랙리스트에 등록하지 않는다")
  void logoutSkipsAccessTokenBlacklistWhenRemainingTtlIsZero() {
    when(jwtTokenProvider.validateToken(ACCESS_TOKEN)).thenReturn(true);
    when(jwtTokenProvider.isAccessToken(ACCESS_TOKEN)).thenReturn(true);
    when(jwtTokenProvider.getJti(ACCESS_TOKEN)).thenReturn(ACCESS_JTI);
    when(jwtTokenProvider.getRemainingValiditySeconds(ACCESS_TOKEN)).thenReturn(0L);

    authService.logout(null, ACCESS_TOKEN);

    verify(accessTokenBlacklistStore, never()).blacklist(Mockito.any(), Mockito.any());
  }

  @Test
  @DisplayName("비활성 계정은 refreshToken이 유효해도 재발급할 수 없다")
  void refreshRejectsInactiveUser() {
    String oldRefreshToken = "old-refresh-token";
    String oldRefreshJti = "old-refresh-jti";

    when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);
    when(jwtTokenProvider.isRefreshToken(oldRefreshToken)).thenReturn(true);
    when(jwtTokenProvider.getJti(oldRefreshToken)).thenReturn(oldRefreshJti);
    when(jwtTokenProvider.getUserId(oldRefreshToken)).thenReturn(USER_ID);
    when(refreshTokenStore.findUserIdByJti(oldRefreshJti)).thenReturn(USER_ID);
    when(userService.getAuthInfoById(USER_ID)).thenReturn(inactiveUser());

    assertAuthError(() -> authService.refresh(oldRefreshToken), AuthErrorCode.INACTIVE_ACCOUNT);
    verify(refreshTokenStore).delete(oldRefreshJti);
    verify(refreshTokenStore, never()).save(Mockito.any(), Mockito.any(), Mockito.any());
    verifyTokenPairNotGenerated();
  }

  private void stubTokenCreation(String accessToken, String refreshToken, String refreshJti) {
    when(jwtTokenProvider.generateAccessToken(USER_ID, Role.USER)).thenReturn(accessToken);
    when(jwtTokenProvider.generateRefreshToken(USER_ID)).thenReturn(refreshToken);
    when(jwtTokenProvider.getJti(refreshToken)).thenReturn(refreshJti);
    when(jwtTokenProvider.getUserId(refreshToken)).thenReturn(USER_ID);
  }

  private UserAuthResponseDto activeUser() {
    return new UserAuthResponseDto(USER_ID, EMAIL, ENCODED_PASSWORD, true, Role.USER);
  }

  private UserAuthResponseDto inactiveUser() {
    return new UserAuthResponseDto(USER_ID, EMAIL, ENCODED_PASSWORD, false, Role.USER);
  }

  private void verifyTokenPairNotGenerated() {
    verify(jwtTokenProvider, never()).generateAccessToken(Mockito.any(), Mockito.any());
    verify(jwtTokenProvider, never()).generateRefreshToken(Mockito.any());
  }

  private void assertAuthError(ThrowingCall call, AuthErrorCode expectedErrorCode) {
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
