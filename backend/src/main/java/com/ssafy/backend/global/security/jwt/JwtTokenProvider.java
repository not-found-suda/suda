package com.ssafy.backend.global.security.jwt;

import com.ssafy.backend.global.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

  private static final String ROLE_CLAIM = "role";
  private static final String TOKEN_TYPE_CLAIM = "token_type";
  private static final String JTI_CLAIM = "jti";
  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final String REFRESH_TOKEN_TYPE = "refresh";

  private final JwtProperties jwtProperties;
  private SecretKey secretKey;

  public JwtTokenProvider(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
  }

  @PostConstruct
  void initialize() {
    byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateAccessToken(Long userId, Role role) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(jwtProperties.getAccessTokenTtlSeconds());
    String jti = UUID.randomUUID().toString();

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuer(jwtProperties.getIssuer())
        .claim(ROLE_CLAIM, role.name())
        .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
        .claim(JTI_CLAIM, jti)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(secretKey)
        .compact();
  }

  public String generateRefreshToken(Long userId) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(jwtProperties.getRefreshTokenTtlSeconds());
    String jti = UUID.randomUUID().toString();

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuer(jwtProperties.getIssuer())
        .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
        .claim(JTI_CLAIM, jti)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(secretKey)
        .compact();
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  public Long getUserId(String token) {
    Claims claims = parseClaims(token);
    return Long.parseLong(claims.getSubject());
  }

  public Role getRole(String token) {
    Claims claims = parseClaims(token);
    String roleName = claims.get(ROLE_CLAIM, String.class);
    return Role.valueOf(roleName);
  }

  public Instant getIssuedAt(String token) {
    Claims claims = parseClaims(token);
    return claims.getIssuedAt().toInstant();
  }

  public boolean isAccessToken(String token) {
    Claims claims = parseClaims(token);
    String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
    return ACCESS_TOKEN_TYPE.equals(tokenType);
  }

  public boolean isRefreshToken(String token) {
    Claims claims = parseClaims(token);
    String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
    return REFRESH_TOKEN_TYPE.equals(tokenType);
  }

  public String getJti(String token) {
    Claims claims = parseClaims(token);
    return claims.get(JTI_CLAIM, String.class);
  }

  public long getRemainingValiditySeconds(String token) {
    Claims claims = parseClaims(token);
    Date expiration = claims.getExpiration();
    long remainingMillis = expiration.getTime() - System.currentTimeMillis();
    return Math.max(0L, remainingMillis / 1000L);
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }
}
