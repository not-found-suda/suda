package com.ssafy.backend.domain.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenInvalidationStore {

  private static final String TOKEN_INVALID_BEFORE_KEY_PREFIX = "user:token-invalid-before:";

  private final StringRedisTemplate redisTemplate;

  public AccessTokenInvalidationStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void invalidateBefore(Long userId, Instant invalidBefore, Duration ttl) {
    if (userId == null || invalidBefore == null || ttl.isZero() || ttl.isNegative()) {
      return;
    }
    redisTemplate
        .opsForValue()
        .set(toKey(userId), String.valueOf(invalidBefore.toEpochMilli()), ttl);
  }

  public boolean isInvalidated(Long userId, Instant issuedAt) {
    if (userId == null || issuedAt == null) {
      return false;
    }

    String value = redisTemplate.opsForValue().get(toKey(userId));
    if (value == null || value.isBlank()) {
      return false;
    }

    try {
      return issuedAt.toEpochMilli() < Long.parseLong(value);
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private String toKey(Long userId) {
    return TOKEN_INVALID_BEFORE_KEY_PREFIX + userId;
  }
}
