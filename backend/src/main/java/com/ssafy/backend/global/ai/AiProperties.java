package com.ssafy.backend.global.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(Gemini gemini, Clova clova) {

  public record Gemini(String apiKey, String model, String baseUrl) {}

  public record Clova(
      String clientId,
      String clientSecret,
      String ttsUrl,
      String speaker,
      String format,
      String sttUrl,
      String sttLanguage) {}
}
