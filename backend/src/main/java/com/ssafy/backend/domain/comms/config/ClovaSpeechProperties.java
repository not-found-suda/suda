package com.ssafy.backend.domain.comms.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.clova-speech")
public record ClovaSpeechProperties(String endpoint, String secretKey, String language, Poc poc) {

  private static final String DEFAULT_ENDPOINT = "clovaspeech-gw.ncloud.com:50051";
  private static final String DEFAULT_LANGUAGE = "ko";

  @Override
  public String endpoint() {
    return hasText(endpoint) ? endpoint : DEFAULT_ENDPOINT;
  }

  @Override
  public String language() {
    return hasText(language) ? language : DEFAULT_LANGUAGE;
  }

  @Override
  public Poc poc() {
    return poc != null ? poc : Poc.disabled();
  }

  public boolean hasSecretKey() {
    return hasText(secretKey);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record Poc(boolean enabled, Path audioPath, boolean skipWavHeader) {

    private static Poc disabled() {
      return new Poc(false, null, false);
    }
  }
}
