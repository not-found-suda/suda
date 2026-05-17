package com.ssafy.backend.domain.auth.service;

import com.ssafy.backend.global.security.jwt.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class UserTokenInvalidationService {

  private final RefreshTokenStore refreshTokenStore;
  private final AccessTokenInvalidationStore accessTokenInvalidationStore;
  private final JwtProperties jwtProperties;

  public UserTokenInvalidationService(
      RefreshTokenStore refreshTokenStore,
      AccessTokenInvalidationStore accessTokenInvalidationStore,
      JwtProperties jwtProperties) {
    this.refreshTokenStore = refreshTokenStore;
    this.accessTokenInvalidationStore = accessTokenInvalidationStore;
    this.jwtProperties = jwtProperties;
  }

  public void invalidateAll(Long userId) {
    refreshTokenStore.deleteAllByUserId(userId);
    accessTokenInvalidationStore.invalidateBefore(
        userId, Instant.now(), Duration.ofSeconds(jwtProperties.getAccessTokenTtlSeconds()));
  }
}
