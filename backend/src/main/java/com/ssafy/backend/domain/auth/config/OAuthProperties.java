package com.ssafy.backend.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Naver naver) {

  public record Naver(String clientId, String clientSecret) {}
}
