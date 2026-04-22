package com.ssafy.backend.domain.auth.service;

import com.ssafy.backend.global.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieManager {

  private final JwtProperties jwtProperties;

  public RefreshTokenCookieManager(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
  }

  public void addRefreshTokenCookie(HttpHeaders headers, String refreshToken) {
    ResponseCookie cookie =
        ResponseCookie.from(jwtProperties.getRefreshTokenCookieName(), refreshToken)
            .httpOnly(true)
            .secure(jwtProperties.isRefreshTokenCookieSecure())
            .path(jwtProperties.getRefreshTokenCookiePath())
            .maxAge(jwtProperties.getRefreshTokenTtlSeconds())
            .sameSite(jwtProperties.getRefreshTokenCookieSameSite())
            .build();
    headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public void expireRefreshTokenCookie(HttpHeaders headers) {
    ResponseCookie cookie =
        ResponseCookie.from(jwtProperties.getRefreshTokenCookieName(), "")
            .httpOnly(true)
            .secure(jwtProperties.isRefreshTokenCookieSecure())
            .path(jwtProperties.getRefreshTokenCookiePath())
            .maxAge(0)
            .sameSite(jwtProperties.getRefreshTokenCookieSameSite())
            .build();
    headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public String resolveRefreshToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null || cookies.length == 0) {
      return null;
    }

    String cookieName = jwtProperties.getRefreshTokenCookieName();
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
