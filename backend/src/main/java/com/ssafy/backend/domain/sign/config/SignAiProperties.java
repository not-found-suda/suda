package com.ssafy.backend.domain.sign.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sign-ai")
public record SignAiProperties(URI baseUrl, Timeout timeout) {

  public record Timeout(Duration connect, Duration read) {}
}
