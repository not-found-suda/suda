package com.ssafy.backend.domain.auth.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenStore {

  private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:jti:";

  private final StringRedisTemplate redisTemplate;

  public RefreshTokenStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void save(String jti, Long userId, Duration ttl) {
    redisTemplate.opsForValue().set(toKey(jti), String.valueOf(userId), ttl);
  }

  public Long findUserIdByJti(String jti) {
    String value = redisTemplate.opsForValue().get(toKey(jti));
    return value != null ? Long.parseLong(value) : null;
  }

  public void delete(String jti) {
    redisTemplate.delete(toKey(jti));
  }

  private String toKey(String jti) {
    return REFRESH_TOKEN_KEY_PREFIX + jti;
  }
}
