package com.ssafy.backend.global.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AssetUrlResolver {

  private final String cloudFrontDomain;

  public AssetUrlResolver(@Value("${app.asset.cloud-front-domain}") String cloudFrontDomain) {
    this.cloudFrontDomain = removeTrailingSlash(cloudFrontDomain);
  }

  public String toUrl(String keyOrUrl) {
    if (keyOrUrl == null || keyOrUrl.isBlank()) {
      return null;
    }

    String normalized = keyOrUrl.trim();

    // 이미 완전한 URL이면 그대로 반환
    if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
      return normalized;
    }

    return cloudFrontDomain + "/" + removeLeadingSlash(normalized);
  }

  private String removeTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String removeLeadingSlash(String value) {
    return value.startsWith("/") ? value.substring(1) : value;
  }
}
