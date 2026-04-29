package com.ssafy.backend.domain.comms.service;

import com.ssafy.backend.global.ai.AiProperties;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class ClovaTtsClient {

  private static final int MAX_TEXT_LENGTH = 500;

  private static final Map<String, String> AUDIO_MIME_TYPES =
      Map.of(
          "mp3", "audio/mpeg",
          "wav", "audio/wav");

  private final RestClient restClient;
  private final AiProperties aiProperties;

  public ClovaTtsClient(RestClient restClient, AiProperties aiProperties) {
    this.restClient = restClient;
    this.aiProperties = aiProperties;
  }

  public byte[] synthesize(String text) {
    String normalizedText = validateAndNormalizeText(text);

    AiProperties.Clova clova = aiProperties.clova();
    String format = normalizeFormat(clova.format());

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("speaker", clova.speaker());
    form.add("format", format);
    form.add("text", normalizedText);
    form.add("speed", "0");

    byte[] audio =
        restClient
            .post()
            .uri(clova.ttsUrl())
            .header("X-NCP-APIGW-API-KEY-ID", clova.clientId())
            .header("X-NCP-APIGW-API-KEY", clova.clientSecret())
            .contentType(
                new MediaType("application", "x-www-form-urlencoded", StandardCharsets.UTF_8))
            .body(form)
            .retrieve()
            .body(byte[].class);

    if (audio == null || audio.length == 0) {
      throw new IllegalStateException("Clova TTS 응답이 비어 있습니다.");
    }

    return audio;
  }

  public String getAudioMimeType() {
    String format = normalizeFormat(aiProperties.clova().format());
    return AUDIO_MIME_TYPES.getOrDefault(format, "audio/" + format);
  }

  private String validateAndNormalizeText(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("TTS 변환할 텍스트는 필수입니다.");
    }

    String normalizedText = text.trim();

    if (normalizedText.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("TTS 변환할 텍스트는 " + MAX_TEXT_LENGTH + "자 이하로 입력해주세요.");
    }

    return normalizedText;
  }

  private String normalizeFormat(String format) {
    if (format == null || format.isBlank()) {
      return "mp3";
    }

    return format.trim().toLowerCase(Locale.ROOT);
  }
}
