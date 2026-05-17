package com.ssafy.backend.domain.auth.service;

import java.time.Duration;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetStore {

  private static final String CODE_KEY_PREFIX = "password-reset:code:";
  private static final String TOKEN_KEY_PREFIX = "password-reset:token:";
  private static final String ATTEMPT_KEY_PREFIX = "password-reset:attempt:";
  private static final String REQUEST_COOLDOWN_KEY_PREFIX = "password-reset:request-cooldown:";

  private final StringRedisTemplate redisTemplate;

  public PasswordResetStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void saveCode(String email, String code, Duration ttl) {
    redisTemplate.opsForValue().set(toCodeKey(email), code, ttl);
    redisTemplate.delete(toAttemptKey(email));
  }

  public String findCodeByEmail(String email) {
    return redisTemplate.opsForValue().get(toCodeKey(email));
  }

  public void deleteCode(String email) {
    redisTemplate.delete(toCodeKey(email));
    redisTemplate.delete(toAttemptKey(email));
  }

  public long incrementVerifyAttempt(String email, Duration ttl) {
    Long attempts = redisTemplate.opsForValue().increment(toAttemptKey(email));
    redisTemplate.expire(toAttemptKey(email), ttl);
    return attempts != null ? attempts : 0;
  }

  public boolean hasRequestCooldown(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(toRequestCooldownKey(email)));
  }

  public void saveRequestCooldown(String email, Duration ttl) {
    redisTemplate.opsForValue().set(toRequestCooldownKey(email), "1", ttl);
  }

  public void saveResetToken(String resetToken, String email, Duration ttl) {
    redisTemplate.opsForValue().set(toTokenKey(resetToken), normalizeEmail(email), ttl);
  }

  public String findEmailByResetToken(String resetToken) {
    return redisTemplate.opsForValue().get(toTokenKey(resetToken));
  }

  public void deleteResetToken(String resetToken) {
    redisTemplate.delete(toTokenKey(resetToken));
  }

  private String toCodeKey(String email) {
    return CODE_KEY_PREFIX + normalizeEmail(email);
  }

  private String toTokenKey(String resetToken) {
    return TOKEN_KEY_PREFIX + resetToken;
  }

  private String toAttemptKey(String email) {
    return ATTEMPT_KEY_PREFIX + normalizeEmail(email);
  }

  private String toRequestCooldownKey(String email) {
    return REQUEST_COOLDOWN_KEY_PREFIX + normalizeEmail(email);
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
