package com.ssafy.backend.domain.comms.service;

import com.ssafy.backend.global.ai.AiProperties;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ClovaSttClient {

  private final RestClient restClient;
  private final AiProperties aiProperties;

  public ClovaSttClient(RestClient restClient, AiProperties aiProperties) {
    this.restClient = restClient;
    this.aiProperties = aiProperties;
  }

  public String transcribe(MultipartFile audioFile) {
    if (audioFile == null || audioFile.isEmpty()) {
      throw new IllegalArgumentException("음성 파일은 필수입니다.");
    }

    try {
      return transcribe(audioFile.getBytes());
    } catch (IOException e) {
      throw new IllegalStateException("음성 파일을 읽는 중 오류가 발생했습니다.", e);
    }
  }

  public String transcribe(byte[] audioBytes) {
    if (audioBytes == null || audioBytes.length == 0) {
      throw new IllegalArgumentException("음성 파일은 필수입니다.");
    }

    AiProperties.Clova clova = aiProperties.clova();

    try {
      ClovaSttResponse response =
          restClient
              .post()
              .uri(clova.sttUrl() + "?lang=" + clova.sttLanguage())
              .header("X-NCP-APIGW-API-KEY-ID", clova.clientId())
              .header("X-NCP-APIGW-API-KEY", clova.clientSecret())
              .contentType(MediaType.APPLICATION_OCTET_STREAM)
              .body(audioBytes)
              .retrieve()
              .body(ClovaSttResponse.class);

      if (response == null || response.text() == null || response.text().isBlank()) {
        throw new IllegalStateException("Clova STT 응답이 비어 있습니다.");
      }

      return response.text().trim();

    } catch (Exception e) {
      throw new IllegalStateException("Clova STT 호출 중 오류가 발생했습니다.", e);
    }
  }

  private record ClovaSttResponse(String text) {}
}
