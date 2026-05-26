package com.ssafy.backend.domain.sign.service;

import com.ssafy.backend.domain.sign.config.SignAiProperties;
import com.ssafy.backend.domain.sign.dto.SignInferenceRequestDto;
import com.ssafy.backend.domain.sign.dto.SignInferenceResponseDto;
import com.ssafy.backend.domain.sign.exception.SignInferenceErrorCode;
import com.ssafy.backend.global.exception.BusinessException;
import com.ssafy.backend.global.logging.TraceIdFilter;
import java.net.SocketTimeoutException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SignAiClient {

  private static final Logger log = LoggerFactory.getLogger(SignAiClient.class);
  private static final String PREDICT_PATH = "/internal/sign/predict";
  private static final int MAX_ERROR_BODY_LOG_LENGTH = 1000;

  private final RestClient restClient;
  private final SignAiProperties properties;

  public SignAiClient(
      @Qualifier("signAiRestClient") RestClient restClient, SignAiProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  public SignInferenceResponseDto predict(SignInferenceRequestDto requestDto) {
    URI uri = UriComponentsBuilder.fromUri(properties.baseUrl()).path(PREDICT_PATH).build().toUri();
    String traceId = getTraceId();
    long startedAtNanos = System.nanoTime();

    try {
      log.info(
          "[SignInference] AI request start. traceId={}, uri={}, sequenceLength={}, featureDimension={}, topK={}, modelVersion={}",
          traceId,
          uri,
          requestDto.sequenceLength(),
          requestDto.featureDimension(),
          requestDto.topK(),
          requestDto.modelVersion());
      SignInferenceResponseDto response =
          restClient
              .post()
              .uri(uri)
              .headers(headers -> addTraceIdHeader(headers, traceId))
              .body(requestDto)
              .retrieve()
              .body(SignInferenceResponseDto.class);
      if (response == null) {
        log.warn(
            "[SignInference] AI empty response. traceId={}, uri={}, elapsedMs={}",
            traceId,
            uri,
            elapsedMs(startedAtNanos));
        throw new BusinessException(SignInferenceErrorCode.AI_SERVER_INVALID_RESPONSE);
      }
      log.info(
          "[SignInference] AI request success. traceId={}, uri={}, elapsedMs={}, modelVersion={}, inferenceMs={}, accepted={}, gloss={}, confidence={}, rejectionReason={}",
          traceId,
          uri,
          elapsedMs(startedAtNanos),
          response.modelVersion(),
          response.inferenceMs(),
          response.accepted(),
          response.gloss(),
          response.confidence(),
          response.rejectionReason());
      return response;
    } catch (BusinessException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      SignInferenceErrorCode errorCode = resolveResponseErrorCode(exception.getStatusCode());
      log.warn(
          "[SignInference] AI error response. traceId={}, uri={}, elapsedMs={}, status={}, code={}, responseBody={}",
          traceId,
          uri,
          elapsedMs(startedAtNanos),
          exception.getStatusCode(),
          errorCode.getCode(),
          abbreviate(exception.getResponseBodyAsString(), MAX_ERROR_BODY_LOG_LENGTH));
      throw new BusinessException(errorCode);
    } catch (ResourceAccessException exception) {
      SignInferenceErrorCode errorCode =
          hasCause(exception, SocketTimeoutException.class)
              ? SignInferenceErrorCode.AI_SERVER_TIMEOUT
              : SignInferenceErrorCode.AI_SERVER_UNAVAILABLE;
      log.warn(
          "[SignInference] AI access failed. traceId={}, uri={}, elapsedMs={}, code={}, exceptionClass={}, message={}",
          traceId,
          uri,
          elapsedMs(startedAtNanos),
          errorCode.getCode(),
          exception.getClass().getName(),
          exception.getMessage());
      throw new BusinessException(errorCode);
    } catch (RestClientException exception) {
      log.warn(
          "[SignInference] AI response handling failed. traceId={}, uri={}, elapsedMs={}, exceptionClass={}, message={}",
          traceId,
          uri,
          elapsedMs(startedAtNanos),
          exception.getClass().getName(),
          exception.getMessage());
      throw new BusinessException(SignInferenceErrorCode.AI_SERVER_INVALID_RESPONSE);
    }
  }

  private void addTraceIdHeader(HttpHeaders headers, String traceId) {
    if (traceId != null && !traceId.isBlank() && !"-".equals(traceId)) {
      headers.set(TraceIdFilter.TRACE_ID_HEADER, traceId);
    }
  }

  private String getTraceId() {
    String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
    return traceId != null && !traceId.isBlank() ? traceId : "-";
  }

  private long elapsedMs(long startedAtNanos) {
    return (System.nanoTime() - startedAtNanos) / 1_000_000;
  }

  private SignInferenceErrorCode resolveResponseErrorCode(HttpStatusCode statusCode) {
    if (statusCode.value() == 504) {
      return SignInferenceErrorCode.AI_SERVER_TIMEOUT;
    }
    if (statusCode.is4xxClientError()) {
      return SignInferenceErrorCode.AI_SERVER_REJECTED_REQUEST;
    }
    return SignInferenceErrorCode.AI_SERVER_UNAVAILABLE;
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

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
    Throwable current = throwable;
    while (current != null) {
      if (causeType.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
