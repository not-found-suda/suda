package com.ssafy.backend.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external-api")
public record ExternalApiProperties(Timeout timeout) {
  public record Timeout(Duration connect, Duration read) {}
}
