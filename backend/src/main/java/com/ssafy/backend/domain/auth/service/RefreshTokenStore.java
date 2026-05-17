package com.ssafy.backend.domain.auth.service;

import java.time.Duration;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenStore {

  private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:jti:";
  private static final String USER_REFRESH_TOKEN_KEY_PREFIX = "user:refresh:";

  private final StringRedisTemplate redisTemplate;

  public RefreshTokenStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void save(String jti, Long userId, Duration ttl) {
    redisTemplate.opsForValue().set(toKey(jti), String.valueOf(userId), ttl);
    redisTemplate.opsForSet().add(toUserKey(userId), jti);
    redisTemplate.expire(toUserKey(userId), ttl);
  }

  public Long findUserIdByJti(String jti) {
    String value = redisTemplate.opsForValue().get(toKey(jti));
    return value != null ? Long.parseLong(value) : null;
  }

  public void delete(String jti) {
    String value = redisTemplate.opsForValue().get(toKey(jti));
    redisTemplate.delete(toKey(jti));
    if (value != null) {
      redisTemplate.opsForSet().remove(toUserKey(Long.parseLong(value)), jti);
    }
  }

  public void deleteAllByUserId(Long userId) {
    String userKey = toUserKey(userId);
    Set<String> jtis = redisTemplate.opsForSet().members(userKey);
    if (jtis != null && !jtis.isEmpty()) {
      redisTemplate.delete(jtis.stream().map(this::toKey).toList());
    }
    redisTemplate.delete(userKey);
  }

  private String toKey(String jti) {
    return REFRESH_TOKEN_KEY_PREFIX + jti;
  }

  private String toUserKey(Long userId) {
    return USER_REFRESH_TOKEN_KEY_PREFIX + userId;
  }
}
