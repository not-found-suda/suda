package com.ssafy.backend.domain.comms.service;

import com.ssafy.backend.global.ai.AiProperties;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ClovaSttClient {

  private static final Logger log = LoggerFactory.getLogger(ClovaSttClient.class);

  private static final int MAX_ERROR_BODY_LOG_LENGTH = 1000;

  private final RestClient restClient;
  private final AiProperties aiProperties;

  public ClovaSttClient(RestClient restClient, AiProperties aiProperties) {
    this.restClient = restClient;
    this.aiProperties = aiProperties;
  }

  public String transcribe(MultipartFile audioFile, String locale, String audioMimeType) {
    if (audioFile == null || audioFile.isEmpty()) {
      throw new IllegalArgumentException("음성 파일은 필수입니다.");
    }

    try {
      return transcribe(audioFile.getBytes(), locale, audioMimeType);
    } catch (IOException e) {
      throw new IllegalStateException("음성 파일을 읽는 중 오류가 발생했습니다.", e);
    }
  }

  public String transcribe(byte[] audioBytes, String locale, String audioMimeType) {
    if (audioBytes == null || audioBytes.length == 0) {
      throw new IllegalArgumentException("음성 파일은 필수입니다.");
    }

    AiProperties.Clova clova = aiProperties.clova();

    String sttLanguage = clova.sttLanguage();
    String sttUrl = clova.sttUrl();
    String requestUrl = sttUrl + "?lang=" + sttLanguage;

    log.info(
        "[STT] Clova request start. traceId={}, endpoint={}, lang={}, locale={}, audioMimeType={}, audioSize={}",
        getTraceId(),
        maskEndpoint(sttUrl),
        sttLanguage,
        locale,
        audioMimeType,
        audioBytes.length);

    try {
      ClovaSttResponse response =
          restClient
              .post()
              .uri(requestUrl)
              .header("X-NCP-APIGW-API-KEY-ID", clova.clientId())
              .header("X-NCP-APIGW-API-KEY", clova.clientSecret())
              .contentType(MediaType.APPLICATION_OCTET_STREAM)
              .body(audioBytes)
              .retrieve()
              .body(ClovaSttResponse.class);

      if (response == null || response.text() == null || response.text().isBlank()) {
        log.warn("[STT] Clova response is empty. traceId={}", getTraceId());
        throw new IllegalStateException("Clova STT 응답이 비어 있습니다.");
      }

      log.info(
          "[STT] Clova request success. traceId={}, recognizedTextLength={}",
          getTraceId(),
          response.text().length());

      return response.text().trim();

    } catch (RestClientResponseException e) {
      log.warn(
          "[STT] Clova request failed. traceId={}, status={}, responseBody={}",
          getTraceId(),
          e.getStatusCode(),
          abbreviate(e.getResponseBodyAsString(), MAX_ERROR_BODY_LOG_LENGTH),
          e);
      throw new IllegalStateException("Clova STT 호출 중 오류가 발생했습니다.", e);

    } catch (Exception e) {
      log.warn(
          "[STT] Clova request exception. traceId={}, exceptionClass={}, message={}",
          getTraceId(),
          e.getClass().getName(),
          e.getMessage(),
          e);
      throw new IllegalStateException("Clova STT 호출 중 오류가 발생했습니다.", e);
    }
  }

  private String abbreviate(String value, int maxLength) {
    if (value == null) {
      return null;
    }

    String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();

    if (normalized.length() <= maxLength) {
      return normalized;
    }

    return normalized.substring(0, maxLength) + "...";
  }

  /** URL 자체는 Secret이 아니지만, 혹시 쿼리스트링이 추가될 가능성을 고려해서 base만 로깅한다. */
  private String maskEndpoint(String endpoint) {
    if (endpoint == null) {
      return null;
    }

    int queryStartIndex = endpoint.indexOf('?');
    if (queryStartIndex < 0) {
      return endpoint;
    }

    return endpoint.substring(0, queryStartIndex);
  }

  private String getTraceId() {
    String traceId = MDC.get("traceId");
    return traceId != null ? traceId : "-";
  }

  private record ClovaSttResponse(String text) {}
}
