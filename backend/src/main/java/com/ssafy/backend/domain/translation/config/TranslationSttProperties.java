package com.ssafy.backend.domain.translation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "translation.stt")
public record TranslationSttProperties(TranslationSttMode mode) {

  @Override
  public TranslationSttMode mode() {
    return mode != null ? mode : TranslationSttMode.REST;
  }
}
