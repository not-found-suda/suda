package com.ssafy.backend.domain.auth.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import com.ssafy.backend.global.security.jwt.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTokenInvalidationServiceTest {

  private static final Long USER_ID = 1L;
  private static final long ACCESS_TOKEN_TTL_SECONDS = 900L;

  @Mock private RefreshTokenStore refreshTokenStore;
  @Mock private AccessTokenInvalidationStore accessTokenInvalidationStore;

  private UserTokenInvalidationService userTokenInvalidationService;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setAccessTokenTtlSeconds(ACCESS_TOKEN_TTL_SECONDS);

    userTokenInvalidationService =
        new UserTokenInvalidationService(
            refreshTokenStore, accessTokenInvalidationStore, jwtProperties);
  }

  @Test
  @DisplayName("사용자 토큰 무효화 시 refreshToken 전체 삭제와 accessToken 기준 시각 저장을 수행한다")
  void invalidateAllDeletesRefreshTokensAndStoresAccessTokenInvalidBefore() {
    userTokenInvalidationService.invalidateAll(USER_ID);

    verify(refreshTokenStore).deleteAllByUserId(USER_ID);
    verify(accessTokenInvalidationStore)
        .invalidateBefore(
            eq(USER_ID), any(Instant.class), eq(Duration.ofSeconds(ACCESS_TOKEN_TTL_SECONDS)));
  }
}
