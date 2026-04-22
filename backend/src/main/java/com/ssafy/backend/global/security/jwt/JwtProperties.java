package com.ssafy.backend.global.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

  @NotBlank private String secret;

  @NotBlank private String issuer;

  @Positive private long accessTokenTtlSeconds;

  @Positive private long refreshTokenTtlSeconds;

  @NotBlank private String refreshTokenCookieName;

  private boolean refreshTokenCookieSecure;

  @NotBlank private String refreshTokenCookieSameSite;

  @NotBlank private String refreshTokenCookiePath;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public long getAccessTokenTtlSeconds() {
    return accessTokenTtlSeconds;
  }

  public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
    this.accessTokenTtlSeconds = accessTokenTtlSeconds;
  }

  public long getRefreshTokenTtlSeconds() {
    return refreshTokenTtlSeconds;
  }

  public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
    this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
  }

  public String getRefreshTokenCookieName() {
    return refreshTokenCookieName;
  }

  public void setRefreshTokenCookieName(String refreshTokenCookieName) {
    this.refreshTokenCookieName = refreshTokenCookieName;
  }

  public boolean isRefreshTokenCookieSecure() {
    return refreshTokenCookieSecure;
  }

  public void setRefreshTokenCookieSecure(boolean refreshTokenCookieSecure) {
    this.refreshTokenCookieSecure = refreshTokenCookieSecure;
  }

  public String getRefreshTokenCookieSameSite() {
    return refreshTokenCookieSameSite;
  }

  public void setRefreshTokenCookieSameSite(String refreshTokenCookieSameSite) {
    this.refreshTokenCookieSameSite = refreshTokenCookieSameSite;
  }

  public String getRefreshTokenCookiePath() {
    return refreshTokenCookiePath;
  }

  public void setRefreshTokenCookiePath(String refreshTokenCookiePath) {
    this.refreshTokenCookiePath = refreshTokenCookiePath;
  }
}
