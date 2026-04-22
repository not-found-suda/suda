package com.ssafy.backend.domain.auth.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenBlacklistStore {

  private static final String ACCESS_BLACKLIST_KEY_PREFIX = "blacklist:access:jti:";

  private final StringRedisTemplate redisTemplate;

  public AccessTokenBlacklistStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void blacklist(String jti, Duration ttl) {
    if (ttl.isZero() || ttl.isNegative()) {
      return;
    }
    redisTemplate.opsForValue().set(toKey(jti), "1", ttl);
  }

  public boolean isBlacklisted(String jti) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(toKey(jti)));
  }

  private String toKey(String jti) {
    return ACCESS_BLACKLIST_KEY_PREFIX + jti;
  }
}
