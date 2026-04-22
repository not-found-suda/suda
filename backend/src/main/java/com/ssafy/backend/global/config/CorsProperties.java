package com.ssafy.backend.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.cors")
public class CorsProperties {

  private List<String> allowedOriginPatterns = List.of();

  private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

  private List<String> allowedHeaders = List.of("*");

  private List<String> exposedHeaders = List.of("Authorization", "X-Trace-Id");

  private boolean allowCredentials = true;

  private long maxAge = 3600L;

  public List<String> getAllowedOriginPatterns() {
    return List.copyOf(allowedOriginPatterns);
  }

  public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
    this.allowedOriginPatterns =
        allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);
  }

  public List<String> getAllowedMethods() {
    return List.copyOf(allowedMethods);
  }

  public void setAllowedMethods(List<String> allowedMethods) {
    this.allowedMethods = allowedMethods == null ? List.of() : List.copyOf(allowedMethods);
  }

  public List<String> getAllowedHeaders() {
    return List.copyOf(allowedHeaders);
  }

  public void setAllowedHeaders(List<String> allowedHeaders) {
    this.allowedHeaders = allowedHeaders == null ? List.of() : List.copyOf(allowedHeaders);
  }

  public List<String> getExposedHeaders() {
    return List.copyOf(exposedHeaders);
  }

  public void setExposedHeaders(List<String> exposedHeaders) {
    this.exposedHeaders = exposedHeaders == null ? List.of() : List.copyOf(exposedHeaders);
  }

  public boolean isAllowCredentials() {
    return allowCredentials;
  }

  public void setAllowCredentials(boolean allowCredentials) {
    this.allowCredentials = allowCredentials;
  }

  public long getMaxAge() {
    return maxAge;
  }

  public void setMaxAge(long maxAge) {
    this.maxAge = maxAge;
  }
}
